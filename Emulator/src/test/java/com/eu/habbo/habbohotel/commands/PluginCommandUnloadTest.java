package com.eu.habbo.habbohotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Commands a plugin registered stayed in the command map after :update_plugins or the jar's removal - still
 * runnable, and keeping the plugin's class loader (and on Windows its jar) alive.
 */
class PluginCommandUnloadTest {

    /** Stands in for a command a plugin jar defines. */
    public static class PluginDefinedCommand extends Command {
        public PluginDefinedCommand() {
            super("cmd_plugin_test", new String[] {"plugintest"});
        }

        @Override
        public boolean handle(GameClient gameClient, String[] params) {
            return true;
        }
    }

    /** Defines one class itself, as a plugin's URLClassLoader defines the classes of its jar. */
    private static final class PluginLoader extends ClassLoader {
        private final String target;

        PluginLoader(ClassLoader parent, String target) {
            super(parent);
            this.target = target;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(this.target)) return super.loadClass(name, resolve);

            synchronized (this.getClassLoadingLock(name)) {
                Class<?> loaded = this.findLoadedClass(name);
                if (loaded != null) return loaded;

                try (InputStream in = this.getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    return this.defineClass(name, bytes, 0, bytes.length);
                } catch (Exception e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }

    @Test
    void theCommandsOfAnUnloadedPluginAreRemovedAndNoOthers() throws Exception {
        PluginLoader loader = new PluginLoader(getClass().getClassLoader(), PluginDefinedCommand.class.getName());
        Class<?> pluginClass = loader.loadClass(PluginDefinedCommand.class.getName());
        CommandHandler.addCommand((Command) pluginClass.getDeclaredConstructor().newInstance());

        assertEquals(1, CommandHandler.removeCommandsLoadedBy(loader));
        assertEquals(0, CommandHandler.removeCommandsLoadedBy(loader), "nothing of it is left");
        assertEquals(0, CommandHandler.removeCommandsLoadedBy(null));
    }

    @Test
    void thePluginManagerRegistersAfterStartAndReleasesEverythingOnUnload() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/plugin/PluginManager.java"));

        assertTrue(source.indexOf("plugin.onEnable();") < source.indexOf("this.plugins.add(plugin);"));
        int dispose = source.indexOf("private void disposePluginsInternal()");
        String body = source.substring(dispose, source.indexOf("this.plugins.clear();", dispose));

        assertTrue(body.indexOf("finally") > body.indexOf("p.onDisable();"));
        assertTrue(body.indexOf("CommandHandler.removeCommandsLoadedBy(p.classLoader)") > body.indexOf("finally"));
        assertTrue(body.indexOf("closeQuietly(p.classLoader") > body.indexOf("finally"));
    }
}
