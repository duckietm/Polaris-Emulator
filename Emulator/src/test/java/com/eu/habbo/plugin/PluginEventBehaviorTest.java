package com.eu.habbo.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.users.Habbo;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginEventBehaviorTest {

    @Test
    void legacyDispatchRunsDefaultHandlersBeforePluginHandlers() throws Exception {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager();
        RecordingPlugin plugin = new RecordingPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);
        DefaultHandler.calls = calls;
        defaultMethods(manager).add(DefaultHandler.class.getMethod("onEvent", TestEvent.class));

        TestEvent event = new TestEvent();

        assertSame(event, manager.fireEvent(event));
        assertEquals(List.of("default", "plugin"), calls);
    }

    @Test
    void legacyDispatchCallsCancelledHandlersRegardlessOfAnnotations() {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager();
        CancellationPlugin plugin = new CancellationPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        TestEvent event = manager.fireEvent(new TestEvent());

        assertTrue(event.isCancelled());
        assertEquals(Set.of("cancel", "ignore-cancelled"), new HashSet<>(calls));
    }

    @Test
    void legacyPluginRegistrationMatchesTheExactEventClassOnly() {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager();
        BaseEventPlugin plugin = new BaseEventPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        manager.fireEvent(new TestEvent());

        assertTrue(plugin.isRegistered(BaseEvent.class));
        assertFalse(plugin.isRegistered(TestEvent.class));
        assertTrue(calls.isEmpty());
    }

    @Test
    void callbackFailureDoesNotStopOtherPluginHandlers() {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager();
        FailingPlugin plugin = new FailingPlugin(calls);
        manager.getPlugins().add(plugin);
        manager.registerEvents(plugin, plugin);

        manager.fireEvent(new TestEvent());

        assertEquals(List.of("completed"), calls);
    }

    @Test
    void legacyDirectRegistrationMutationsTakeEffectWithoutManagerRegistration() throws Exception {
        List<String> calls = new ArrayList<>();
        PluginManager manager = new PluginManager();
        RecordingPlugin plugin = new RecordingPlugin(calls);
        manager.getPlugins().add(plugin);
        Method method = RecordingPlugin.class.getMethod("onDirectEvent", TestEvent.class);

        plugin.registeredEvents.put(TestEvent.class, new HashSet<>(Set.of(method)));
        manager.fireEvent(new TestEvent());

        plugin.registeredEvents.get(TestEvent.class).clear();
        manager.fireEvent(new TestEvent());

        assertEquals(List.of("plugin"), calls);
    }

    @SuppressWarnings("unchecked")
    private static Set<Method> defaultMethods(PluginManager manager) throws Exception {
        Field field = PluginManager.class.getDeclaredField("methods");
        field.setAccessible(true);
        return (Set<Method>) field.get(manager);
    }

    public static final class DefaultHandler {
        private static List<String> calls;

        @EventHandler
        public static void onEvent(TestEvent event) {
            calls.add("default");
        }
    }

    private abstract static class TestPlugin extends HabboPlugin implements EventListener {
        final List<String> calls;

        TestPlugin(List<String> calls) {
            this.calls = calls;
            this.configuration = new HabboPluginConfiguration();
            this.configuration.name = getClass().getSimpleName();
        }

        @Override
        public void onEnable() {}

        @Override
        public void onDisable() {}

        @Override
        public boolean hasPermission(Habbo habbo, String key) {
            return true;
        }
    }

    private static final class RecordingPlugin extends TestPlugin {
        RecordingPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler
        public void onEvent(TestEvent event) {
            calls.add("plugin");
        }

        public void onDirectEvent(TestEvent event) {
            calls.add("plugin");
        }
    }

    private static final class CancellationPlugin extends TestPlugin {
        CancellationPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void cancel(TestEvent event) {
            calls.add("cancel");
            event.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void receiveCancelled(TestEvent event) {
            calls.add("ignore-cancelled");
        }
    }

    private static final class BaseEventPlugin extends TestPlugin {
        BaseEventPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler
        public void onBaseEvent(BaseEvent event) {
            calls.add("base");
        }
    }

    private static final class FailingPlugin extends TestPlugin {
        FailingPlugin(List<String> calls) {
            super(calls);
        }

        @EventHandler
        public void fail(TestEvent event) {
            throw new IllegalStateException("fixture callback failure");
        }

        @EventHandler
        public void complete(TestEvent event) {
            calls.add("completed");
        }
    }

    private abstract static class BaseEvent extends Event {}

    private static final class TestEvent extends BaseEvent {}
}
