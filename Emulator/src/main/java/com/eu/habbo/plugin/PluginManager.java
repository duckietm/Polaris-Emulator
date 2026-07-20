package com.eu.habbo.plugin;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.Easter;
import com.eu.habbo.habbohotel.games.freeze.FreezeGame;
import com.eu.habbo.habbohotel.games.tag.TagGame;
import com.eu.habbo.habbohotel.items.interactions.games.football.InteractionFootballGate;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreManager;
import com.eu.habbo.messages.PacketManager;
import com.eu.habbo.messages.RuntimeValidationReport;
import com.eu.habbo.plugin.events.emulator.EmulatorConfigUpdatedEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.roomunit.RoomUnitLookAtPointEvent;
import com.eu.habbo.plugin.events.users.UserDisconnectEvent;
import com.eu.habbo.plugin.events.users.UserExitRoomEvent;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import com.eu.habbo.plugin.events.users.UserSavedMottoEvent;
import com.eu.habbo.plugin.events.users.UserTakeStepEvent;
import com.eu.habbo.threading.runnables.RoomTrashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    // Gson is thread-safe and immutable once built — reuse one instance instead
    // of building a parser per plugin-config load.
    private static final Gson PLUGIN_GSON = new GsonBuilder().create();

    private final Set<HabboPlugin> plugins = new HashSet<>();
    private final Set<Method> methods = new HashSet<>();

    @EventHandler
    public static void globalOnConfigurationUpdated(EmulatorConfigUpdatedEvent event) {
        var configuration = Emulator.getConfig();
        boolean runtimeReady = Emulator.isReady;
        new RoomConfigurationBinder(configuration, PluginManager::parsePaydayTimestamp).bind();
        new WiredConfigurationBinder(configuration).bind();
        new NetworkConfigurationBinder(configuration).bind();
        new CatalogConfigurationBinder(configuration, runtimeReady).bind();

        if (runtimeReady) {
            Emulator.getGameEnvironment().getCreditsScheduler().reloadConfig();
            Emulator.getGameEnvironment().getPointsScheduler().reloadConfig();
            Emulator.getGameEnvironment().getPixelScheduler().reloadConfig();
            Emulator.getGameEnvironment().getGotwPointsScheduler().reloadConfig();
            Emulator.getGameEnvironment().subscriptionScheduler.reloadConfig();
        }
    }

    static long parsePaydayTimestamp(String value) {
        ParsePosition position = new ParsePosition(0);
        Date parsed = value == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(value, position);
        if (parsed == null) {
            LOGGER.warn(
                    "Invalid subscriptions.hc.payday.next_date '{}' "
                            + "(expected yyyy-MM-dd HH:mm:ss); "
                            + "HC payday is paused until it is corrected.",
                    value);
            return Integer.MAX_VALUE;
        }
        long timestamp = parsed.getTime() / 1000L;
        if (timestamp > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (timestamp < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return timestamp;
    }

    public void loadPlugins() {
        this.disposePlugins();

        File loc = new File("plugins");

        if (!loc.exists()) {
            if (loc.mkdirs()) {
                LOGGER.info("Created plugins directory!");
            }
        }

        for (File file : Objects.requireNonNull(
                loc.listFiles(file -> file.getPath().toLowerCase().endsWith(".jar")))) {
            URLClassLoader urlClassLoader = null;
            InputStream stream = null;
            boolean retainPluginResources = false;

            try {
                urlClassLoader =
                        URLClassLoader.newInstance(new URL[] {file.toURI().toURL()});
                stream = urlClassLoader.getResourceAsStream("plugin.json");

                if (stream == null) {
                    throw new RuntimeException("Invalid Jar! Missing plugin.json in: " + file.getName());
                }

                byte[] content = new byte[stream.available()];

                if (stream.read(content) > 0) {
                    String body = new String(content, java.nio.charset.StandardCharsets.UTF_8);

                    HabboPluginConfiguration pluginConfigurtion =
                            PLUGIN_GSON.fromJson(body, HabboPluginConfiguration.class);
                    RuntimeValidationReport validationReport = PluginRuntimeValidator.validatePluginClass(
                            file.getName(), pluginConfigurtion, urlClassLoader);

                    if (validationReport.hasErrors()) {
                        validationReport.logErrors(LOGGER, "Plugin validation");
                        continue;
                    }

                    try {
                        Class<?> clazz = urlClassLoader.loadClass(pluginConfigurtion.main);
                        Class<? extends HabboPlugin> stackClazz = clazz.asSubclass(HabboPlugin.class);
                        Constructor<? extends HabboPlugin> constructor = stackClazz.getConstructor();
                        HabboPlugin plugin = constructor.newInstance();
                        plugin.configuration = pluginConfigurtion;
                        plugin.classLoader = urlClassLoader;
                        plugin.stream = stream;
                        this.plugins.add(plugin);
                        retainPluginResources = true;
                        plugin.onEnable();
                    } catch (Exception e) {
                        LOGGER.error("Could not load plugin {}!", pluginConfigurtion.name);
                        LOGGER.error("Caught exception", e);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("Caught exception", e);
            } finally {
                if (!retainPluginResources) {
                    closeRejectedPluginResources(stream, urlClassLoader, file.getName());
                }
            }
        }
    }

    private static void closeRejectedPluginResources(InputStream stream, URLClassLoader classLoader, String jarName) {
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close plugin.json stream for rejected plugin {}.", jarName, e);
            }
        }

        if (classLoader != null) {
            try {
                classLoader.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close classloader for rejected plugin {}.", jarName, e);
            }
        }
    }

    public void registerEvents(HabboPlugin plugin, EventListener listener) {
        synchronized (plugin.registeredEvents) {
            Method[] methods = listener.getClass().getMethods();

            for (Method method : methods) {
                if (method.getAnnotation(EventHandler.class) != null) {
                    if (method.getParameterTypes().length == 1) {
                        if (Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                            final Class<?> eventClass = method.getParameterTypes()[0];

                            if (!plugin.registeredEvents.containsKey(eventClass.asSubclass(Event.class))) {
                                plugin.registeredEvents.put(eventClass.asSubclass(Event.class), new HashSet<>());
                            }

                            plugin.registeredEvents
                                    .get(eventClass.asSubclass(Event.class))
                                    .add(method);
                        }
                    }
                }
            }
        }
    }

    public <T extends Event> T fireEvent(T event) {
        for (Method method : this.methods) {
            if (method.getParameterTypes().length == 1
                    && method.getParameterTypes()[0].isAssignableFrom(event.getClass())) {
                try {
                    method.invoke(null, event);
                } catch (Exception e) {
                    LOGGER.error(
                            "Could not pass default event {} to {}: {}!",
                            event.getClass().getName(),
                            method.getClass().getName(),
                            method.getName());
                    LOGGER.error("Caught exception", e);
                }
            }
        }

        for (HabboPlugin plugin : this.plugins) {

            if (plugin != null) {
                Set<Method> methods =
                        plugin.registeredEvents.get(event.getClass().asSubclass(Event.class));

                if (methods != null) {
                    for (Method method : methods) {
                        try {
                            method.invoke(plugin, event);
                        } catch (Exception e) {
                            LOGGER.error(
                                    "Could not pass event {} to {}",
                                    event.getClass().getName(),
                                    plugin.configuration.name);
                            LOGGER.error("Caught exception", e);
                        }
                    }
                }
            }
        }

        return event;
    }

    public boolean isRegistered(Class<? extends Event> clazz, boolean pluginsOnly) {
        for (HabboPlugin plugin : this.plugins) {
            if (plugin != null && plugin.isRegistered(clazz)) {
                return true;
            }
        }

        if (!pluginsOnly) {
            for (Method method : this.methods) {
                if (method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(clazz)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void dispose() {
        this.disposePlugins();

        LOGGER.info("Disposed Plugin Manager!");
    }

    private void disposePlugins() {
        for (HabboPlugin p : this.plugins) {
            if (p != null) {

                try {
                    p.onDisable();
                    p.stream.close();
                    p.classLoader.close();
                } catch (IOException e) {
                    LOGGER.error("Caught exception", e);
                } catch (Exception ex) {
                    LOGGER.error("Failed to disable {} because of an exception.", p.configuration.name, ex);
                }
            }
        }
        this.plugins.clear();
    }

    public void reload() {
        long millis = System.currentTimeMillis();

        this.methods.clear();

        this.loadPlugins();

        LOGGER.info(
                "Plugin Manager -> Loaded! {} plugins! ({} MS)",
                this.plugins.size(),
                System.currentTimeMillis() - millis);

        this.registerDefaultEvents();
    }

    private void registerDefaultEvents() {
        try {
            this.methods.add(RoomTrashing.class.getMethod("onUserWalkEvent", UserTakeStepEvent.class));
            this.methods.add(Easter.class.getMethod("onUserChangeMotto", UserSavedMottoEvent.class));
            this.methods.add(TagGame.class.getMethod("onUserLookAtPoint", RoomUnitLookAtPointEvent.class));
            this.methods.add(TagGame.class.getMethod("onUserWalkEvent", UserTakeStepEvent.class));
            this.methods.add(FreezeGame.class.getMethod("onConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(PacketManager.class.getMethod("onConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(
                    InteractionFootballGate.class.getMethod("onUserDisconnectEvent", UserDisconnectEvent.class));
            this.methods.add(InteractionFootballGate.class.getMethod("onUserExitRoomEvent", UserExitRoomEvent.class));
            this.methods.add(InteractionFootballGate.class.getMethod("onUserSavedLookEvent", UserSavedLookEvent.class));
            this.methods.add(
                    PluginManager.class.getMethod("globalOnConfigurationUpdated", EmulatorConfigUpdatedEvent.class));
            this.methods.add(WiredHighscoreManager.class.getMethod("onEmulatorLoaded", EmulatorLoadedEvent.class));
            this.methods.add(WiredManager.class.getMethod("onEmulatorLoaded", EmulatorLoadedEvent.class));
        } catch (NoSuchMethodException e) {
            LOGGER.info("Failed to define default events!");
            LOGGER.error("Caught exception", e);
        }
    }

    public Set<HabboPlugin> getPlugins() {
        return this.plugins;
    }
}
