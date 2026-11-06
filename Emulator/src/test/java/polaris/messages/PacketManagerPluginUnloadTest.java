package polaris.messages;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import polaris.messages.incoming.MessageHandler;

/**
 * :update_plugins closed a plugin's class loader but left its packet handlers and callables registered: the old
 * callables kept running on every packet, and the reloaded plugin could not register its handler because the header
 * was taken.
 */
class PacketManagerPluginUnloadTest {

    /** A handler and a callable as a plugin jar would define them. */
    public static class PluginHandler extends MessageHandler {
        @Override
        public void handle() {}
    }

    public static class PluginCallable implements ICallable {
        @Override
        public void call(MessageHandler handler) {}
    }

    /** Defines the two classes above again, the way a plugin's own class loader does. */
    private static final class PluginLoader extends ClassLoader {
        PluginLoader() {
            super(PacketManagerPluginUnloadTest.class.getClassLoader());
        }

        Class<?> define(Class<?> type) throws Exception {
            try (InputStream bytes =
                    getParent().getResourceAsStream(type.getName().replace('.', '/') + ".class")) {
                byte[] content = bytes.readAllBytes();
                return defineClass(type.getName(), content, 0, content.length);
            }
        }
    }

    private Map<Integer, Class<? extends MessageHandler>> incoming;
    private Map<Integer, List<ICallable>> callables;

    private PacketManager manager() throws Exception {
        PacketManager manager = mock(PacketManager.class, CALLS_REAL_METHODS);
        this.incoming = new ConcurrentHashMap<>();
        this.callables = new ConcurrentHashMap<>();
        set(manager, "incoming", this.incoming);
        set(manager, "callables", this.callables);
        return manager;
    }

    private static void set(PacketManager manager, String name, Object value) throws Exception {
        Field field = PacketManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnloadedPluginTakesItsHandlersAndCallablesWithIt() throws Exception {
        PacketManager manager = this.manager();
        PluginLoader plugin = new PluginLoader();
        Class<? extends MessageHandler> pluginHandler =
                (Class<? extends MessageHandler>) plugin.define(PluginHandler.class);
        ICallable pluginCallable = (ICallable)
                plugin.define(PluginCallable.class).getDeclaredConstructor().newInstance();
        ICallable coreCallable = new PluginCallable();

        manager.registerHandler(9001, pluginHandler);
        manager.registerHandler(9002, PluginHandler.class);
        manager.registerCallable(9001, pluginCallable);
        manager.registerCallable(9001, coreCallable);
        manager.registerCallable(9003, pluginCallable);

        assertEquals(3, manager.removeLoadedBy(plugin));

        assertFalse(this.incoming.containsKey(9001));
        assertSame(PluginHandler.class, this.incoming.get(9002));
        assertEquals(List.of(coreCallable), this.callables.get(9001));
        assertFalse(this.callables.containsKey(9003), "an emptied header is dropped");
        // The reloaded plugin registers its handler again instead of failing on a taken header.
        assertDoesNotThrow(() -> manager.registerHandler(9001, pluginHandler));
    }

    @Test
    void aHeaderIsStillRegisteredOnlyOnce() throws Exception {
        PacketManager manager = this.manager();
        manager.registerHandler(9001, PluginHandler.class);

        assertThrows(Exception.class, () -> manager.registerHandler(9001, PluginHandler.class));
    }
}
