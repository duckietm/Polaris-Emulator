package com.eu.habbo.packaging.probe;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.EventListener;
import com.eu.habbo.plugin.HabboPlugin;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.furniture.wired.WiredConditionFailedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackExecutedEvent;
import com.eu.habbo.plugin.events.furniture.wired.WiredStackTriggeredEvent;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Properties;
import java.util.ServiceLoader;
import org.flywaydb.core.extensibility.Plugin;

public final class PackagedJarProbe {

    private PackagedJarProbe() {}

    public static void main(String[] args) throws Exception {
        Properties project = new Properties();
        try (InputStream pom =
                PackagedJarProbe.class.getResourceAsStream("/META-INF/maven/com.eu.habbo/Polaris/pom.properties")) {
            require(pom != null, "Packaged JAR is missing its Maven project metadata");
            project.load(pom);
        }

        Package emulatorPackage = Emulator.class.getPackage();
        require(
                Objects.equals(project.getProperty("version"), emulatorPackage.getImplementationVersion()),
                "Implementation-Version does not match the Maven project version");
        require(
                Objects.equals(project.getProperty("artifactId"), emulatorPackage.getImplementationTitle()),
                "Implementation-Title does not match the Maven artifact name");

        boolean mariaDbProviderDiscovered = ServiceLoader.load(Plugin.class).stream()
                .map(ServiceLoader.Provider::type)
                .map(Class::getName)
                .anyMatch("org.flywaydb.database.mysql.mariadb.MariaDBDatabaseType"::equals);
        require(mariaDbProviderDiscovered, "Flyway MariaDB service provider was not discovered");
        verifyReleasedPolarisTroveBehavior();

        if (args.length == 1 && "plugin".equals(args[0])) {
            verifyLegacyPlugin();
        }

        System.out.println("Packaged JAR contract verified");
    }

    private static void verifyReleasedPolarisTroveBehavior() throws Exception {
        Class<?> type = Class.forName("gnu.trove.map.hash.THashMap");
        require(type.getSuperclass() == HashMap.class, "Released Polaris THashMap superclass behavior changed");
        Object map = type.getConstructor().newInstance();
        Object value = type.getMethod("computeIfAbsent", Object.class, java.util.function.Function.class)
                .invoke(map, "answer", (java.util.function.Function<Object, Object>) ignored -> 42);
        require(Objects.equals(42, value), "Released Polaris THashMap inherited Map behavior changed");
    }

    private static void verifyLegacyPlugin() throws Exception {
        prepareMinimalEmulatorConfig();
        PluginManager manager = new PluginManager();
        manager.reload();
        require(manager.getPlugins().size() == 1, "Expected one legacy plugin fixture");
        HabboPlugin plugin = manager.getPlugins().iterator().next();

        String pluginName = plugin.configuration.name;
        require(
                "Morningstar compatibility fixture".equals(pluginName)
                        || "Released Polaris compatibility fixture".equals(pluginName)
                        || "Current WIRED snapshot provider fixture".equals(pluginName),
                "Unexpected plugin.json name: " + pluginName);
        require("Polaris tests".equals(plugin.configuration.author), "plugin.json author was not preserved");
        require(
                plugin.getClass().getClassLoader() == plugin.classLoader,
                "Plugin class was not owned by its plugin classloader");
        require((boolean) plugin.getClass().getMethod("isEnabled").invoke(plugin), "Plugin onEnable was not called");
        if (pluginName.startsWith("Current WIRED snapshot")) {
            manager.dispose();
            require(
                    !(boolean) plugin.getClass().getMethod("isEnabled").invoke(plugin),
                    "Current WIRED snapshot plugin onDisable was not called");
            System.out.println("Legacy plugin contract verified");
            return;
        }
        String expectedResource =
                pluginName.startsWith("Morningstar") ? "legacy-plugin-resource" : "released-plugin-resource";
        require(
                expectedResource.equals(
                        plugin.getClass().getMethod("readOwnResource").invoke(plugin)),
                "Plugin-owned resource was not visible");
        require(plugin.hasPermission(null, "fixture.allowed"), "Legacy permission callback behavior changed");

        require(
                (boolean) plugin.getClass()
                        .getMethod("wiredHandlerSurfaceIsCompatible")
                        .invoke(plugin),
                "Legacy WiredHandler surface did not link");
        require(
                (boolean) plugin.getClass()
                        .getMethod("wiredManagerSurfaceIsCompatible")
                        .invoke(plugin),
                "Current WiredManager compatibility surface did not resolve");
        if (!pluginName.startsWith("Morningstar")) {
            require(
                    (boolean) plugin.getClass()
                            .getMethod("releasedCollectionBehavior")
                            .invoke(plugin),
                    "Released Polaris collection behavior changed");
        }

        manager.registerEvents(plugin, (EventListener) plugin);
        WiredStackTriggeredEvent triggered =
                new WiredStackTriggeredEvent(null, null, null, Collections.emptySet(), Collections.emptySet());
        manager.fireEvent(triggered);
        manager.fireEvent(
                new WiredStackExecutedEvent(null, null, null, Collections.emptySet(), Collections.emptySet()));
        manager.fireEvent(new WiredConditionFailedEvent(null, null, null, null));
        require(triggered.isCancelled(), "Wired triggered-event cancellation was not preserved");
        require(
                (int) plugin.getClass().getMethod("getWiredTriggeredCount").invoke(plugin) == 1,
                "Wired triggered event was not dispatched exactly once");
        require(
                (int) plugin.getClass().getMethod("getWiredExecutedCount").invoke(plugin) == 1,
                "Wired executed event was not dispatched exactly once");
        require(
                (int) plugin.getClass()
                                .getMethod("getWiredConditionFailedCount")
                                .invoke(plugin)
                        == 1,
                "Wired condition-failed event was not dispatched exactly once");

        if (pluginName.startsWith("Morningstar")) {
            require(
                    (boolean) plugin.getClass()
                            .getMethod("bundledClasspathVisible")
                            .invoke(plugin),
                    "Bundled plugin classpath was not visible");
            require(
                    (boolean) plugin.getClass()
                            .getMethod("databaseBridgeSignatureIsCompatible")
                            .invoke(plugin),
                    "Legacy database bridge signature changed");
            Class<?> eventType = plugin.classLoader.loadClass("fixture.morningstar.LegacyBehaviorPlugin$FixtureEvent");
            Event event = (Event) eventType.getConstructor().newInstance();
            manager.fireEvent(event);
            require(
                    (int) plugin.getClass().getMethod("getEventCount").invoke(plugin) == 1,
                    "Legacy plugin event callback was not dispatched");

            try {
                plugin.getClass().getMethod("useMorningstarTroveSurface").invoke(plugin);
                throw new IllegalStateException("Morningstar-only Trove surface unexpectedly linked");
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                require(
                        cause instanceof NoClassDefFoundError || cause instanceof NoSuchMethodError,
                        "Unexpected Morningstar Trove linkage result: " + cause);
            }
        }

        manager.dispose();
        require((boolean) plugin.getClass().getMethod("isDisabled").invoke(plugin), "Plugin onDisable was not called");
        System.out.println("Legacy plugin contract verified");
    }

    private static void prepareMinimalEmulatorConfig() throws Exception {
        Path config = Path.of("packaged-wired-probe.ini");
        Files.writeString(
                config,
                "hotel.wired.furni.selection.count=5\nwired.effect.teleport.delay=500\n",
                StandardCharsets.UTF_8);
        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, new ConfigurationManager(config.toString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
