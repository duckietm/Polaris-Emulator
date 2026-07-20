package com.eu.habbo.plugin;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(PluginManager.class);

    // Gson is thread-safe and immutable once built — reuse one instance instead
    // of building a parser per plugin-config load.
    private static final Gson PLUGIN_GSON = new GsonBuilder().create();

    private final Set<HabboPlugin> plugins = new HashSet<>();
    private final Set<Method> methods = new HashSet<>();
    private final BooleanSupplier honorPriority;
    private final Object dispatchStateLock = new Object();
    private volatile DispatchSnapshot dispatchSnapshot = DispatchSnapshot.empty();
    private volatile boolean reloading;

    public PluginManager() {
        this(() -> false);
    }

    public PluginManager(ConfigurationManager configuration) {
        this(prioritySetting(configuration));
    }

    PluginManager(BooleanSupplier honorPriority) {
        this.honorPriority = Objects.requireNonNull(honorPriority);
    }

    private static BooleanSupplier prioritySetting(ConfigurationManager configuration) {
        ConfigurationManager requiredConfiguration = Objects.requireNonNull(configuration);
        return () -> requiredConfiguration.getBoolean("polaris.events.honor_priority", false);
    }

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
        synchronized (this.dispatchStateLock) {
            this.reloading = true;
            try {
                this.loadPluginsInternal();
            } finally {
                try {
                    this.publishDispatchSnapshot();
                } finally {
                    this.reloading = false;
                }
            }
        }
    }

    private void loadPluginsInternal() {
        this.disposePluginsInternal();

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
        synchronized (this.dispatchStateLock) {
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

            if (!this.reloading) {
                this.publishDispatchSnapshot();
            }
        }
    }

    public <T extends Event> T fireEvent(T event) {
        Class<? extends Event> eventType = event.getClass().asSubclass(Event.class);
        DispatchSnapshot snapshot = this.currentDispatchSnapshot(eventType);
        boolean corrected = this.honorPriority.getAsBoolean();
        List<HandlerInvocation> handlers = snapshot.handlersFor(event, corrected);

        for (HandlerInvocation handler : handlers) {
            if (corrected && event.isCancelled() && handler.ignoresCancelled()) {
                continue;
            }
            handler.invoke(event);
        }

        return event;
    }

    public boolean isRegistered(Class<? extends Event> clazz, boolean pluginsOnly) {
        DispatchSnapshot snapshot = this.currentDispatchSnapshot(clazz);
        if (snapshot.hasPluginHandler(clazz)) {
            return true;
        }

        return !pluginsOnly && snapshot.hasDefaultHandler(clazz);
    }

    public void dispose() {
        synchronized (this.dispatchStateLock) {
            this.reloading = true;
            try {
                this.disposePluginsInternal();
            } finally {
                try {
                    this.publishDispatchSnapshot();
                } finally {
                    this.reloading = false;
                }
            }
        }

        LOGGER.info("Disposed Plugin Manager!");
    }

    private void disposePluginsInternal() {
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

        synchronized (this.dispatchStateLock) {
            this.reloading = true;
            try {
                this.methods.clear();
                this.loadPluginsInternal();
                this.registerDefaultEvents();
            } finally {
                try {
                    this.publishDispatchSnapshot();
                } finally {
                    this.reloading = false;
                }
            }
        }

        LOGGER.info(
                "Plugin Manager -> Loaded! {} plugins! ({} MS)",
                this.plugins.size(),
                System.currentTimeMillis() - millis);
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

    private DispatchSnapshot currentDispatchSnapshot(Class<? extends Event> eventType) {
        DispatchSnapshot snapshot = this.dispatchSnapshot;
        if (!this.reloading
                && (snapshot.defaultHandlerCount() != this.methods.size()
                        || !snapshot.matchesPluginRegistrations(this.plugins, eventType))) {
            synchronized (this.dispatchStateLock) {
                if (!this.reloading
                        && (this.dispatchSnapshot.defaultHandlerCount() != this.methods.size()
                                || !this.dispatchSnapshot.matchesPluginRegistrations(this.plugins, eventType))) {
                    this.publishDispatchSnapshot();
                }
                snapshot = this.dispatchSnapshot;
            }
        }
        return snapshot;
    }

    private void publishDispatchSnapshot() {
        this.dispatchSnapshot = DispatchSnapshot.capture(this.methods, this.plugins);
    }

    private record HandlerInvocation(
            HabboPlugin plugin,
            Method method,
            EventHandler annotation,
            Class<? extends Event> eventType,
            MethodHandle handle,
            boolean staticMethod) {

        private static final Comparator<HandlerInvocation> CORRECTED_ORDER =
                Comparator.comparingInt(HandlerInvocation::prioritySlot).thenComparing(HandlerInvocation::stableKey);

        static HandlerInvocation defaultHandler(Method method) {
            return create(null, method);
        }

        static HandlerInvocation pluginHandler(HabboPlugin plugin, Method method) {
            return create(plugin, method);
        }

        private static HandlerInvocation create(HabboPlugin plugin, Method method) {
            try {
                method.trySetAccessible();
                return new HandlerInvocation(
                        plugin,
                        method,
                        method.getAnnotation(EventHandler.class),
                        method.getParameterTypes()[0].asSubclass(Event.class),
                        MethodHandles.lookup().unreflect(method),
                        Modifier.isStatic(method.getModifiers()));
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Unable to cache plugin event handler " + method, exception);
            }
        }

        private String stableKey() {
            String pluginKey = "";
            if (this.plugin != null) {
                String pluginName = this.plugin.configuration == null
                        ? this.plugin.getClass().getName()
                        : this.plugin.configuration.name;
                URL[] pluginUrls = this.plugin.classLoader == null ? new URL[0] : this.plugin.classLoader.getURLs();
                pluginKey = Objects.toString(pluginName, "") + Arrays.toString(pluginUrls);
            }
            return pluginKey
                    + '|'
                    + this.method.getDeclaringClass().getName()
                    + '#'
                    + this.method.getName()
                    + Arrays.toString(this.method.getParameterTypes());
        }

        private int prioritySlot() {
            return this.annotation == null
                    ? EventPriority.NORMAL.getSlot()
                    : this.annotation.priority().getSlot();
        }

        private boolean ignoresCancelled() {
            return this.annotation != null && this.annotation.ignoreCancelled();
        }

        void invoke(Event event) {
            try {
                // The receiver is bound only for instance methods. A static @EventHandler
                // (the classic Arcturus/Morningstar pattern) unreflects to a no-receiver
                // handle, so passing this.plugin would raise WrongMethodTypeException.
                // Legacy reflection ignored the receiver for static methods; preserve that.
                if (this.staticMethod) {
                    this.handle.invoke(event);
                } else {
                    this.handle.invoke(this.plugin, event);
                }
            } catch (Throwable exception) {
                if (this.plugin == null) {
                    LOGGER.error(
                            "Could not pass default event {} to {}:{}!",
                            event.getClass().getName(),
                            this.method.getDeclaringClass().getName(),
                            this.method.getName());
                } else {
                    String pluginName = this.plugin.configuration == null
                            ? this.plugin.getClass().getName()
                            : this.plugin.configuration.name;
                    LOGGER.error(
                            "Could not pass event {} to {}", event.getClass().getName(), pluginName);
                }
                LOGGER.error("Caught exception", exception);
            }
        }
    }

    private record DispatchSnapshot(
            List<HandlerInvocation> defaultHandlers,
            Map<Class<? extends Event>, List<HandlerInvocation>> pluginHandlers,
            Map<Class<? extends Event>, List<RegistrationSource>> pluginRegistrationSources,
            List<HabboPlugin> capturedPlugins,
            ConcurrentMap<Class<? extends Event>, HandlerLists> handlersByEventType) {

        static DispatchSnapshot empty() {
            return new DispatchSnapshot(List.of(), Map.of(), Map.of(), List.of(), new ConcurrentHashMap<>());
        }

        static DispatchSnapshot capture(Set<Method> methods, Set<HabboPlugin> plugins) {
            List<HandlerInvocation> defaults = methods.stream()
                    .filter(method -> method.getAnnotation(EventHandler.class) != null)
                    .map(HandlerInvocation::defaultHandler)
                    .toList();
            Map<Class<? extends Event>, List<HandlerInvocation>> handlers = new HashMap<>();
            Map<Class<? extends Event>, List<RegistrationSource>> registrationSources = new HashMap<>();

            for (HabboPlugin plugin : plugins) {
                if (plugin == null) {
                    continue;
                }
                synchronized (plugin.registeredEvents) {
                    plugin.registeredEvents.forEach((eventType, registeredMethods) -> {
                        Set<Method> registeredMethodsSnapshot = Set.copyOf(registeredMethods);
                        registrationSources
                                .computeIfAbsent(eventType, ignored -> new ArrayList<>())
                                .add(new RegistrationSource(plugin, registeredMethodsSnapshot));
                        List<HandlerInvocation> eventHandlers =
                                handlers.computeIfAbsent(eventType, ignored -> new ArrayList<>());
                        registeredMethodsSnapshot.stream()
                                .map(method -> HandlerInvocation.pluginHandler(plugin, method))
                                .forEach(eventHandlers::add);
                    });
                }
            }

            handlers.replaceAll((ignored, eventHandlers) -> List.copyOf(eventHandlers));
            registrationSources.replaceAll((ignored, sources) -> List.copyOf(sources));
            return new DispatchSnapshot(
                    List.copyOf(defaults),
                    Map.copyOf(handlers),
                    Map.copyOf(registrationSources),
                    plugins.stream().toList(),
                    new ConcurrentHashMap<>());
        }

        int defaultHandlerCount() {
            return this.defaultHandlers.size();
        }

        boolean hasPluginHandler(Class<? extends Event> eventType) {
            return this.pluginHandlers.containsKey(eventType);
        }

        boolean hasDefaultHandler(Class<? extends Event> eventType) {
            return this.defaultHandlers.stream()
                    .anyMatch(handler -> handler.eventType().isAssignableFrom(eventType));
        }

        boolean matchesPluginRegistrations(Set<HabboPlugin> plugins, Class<? extends Event> eventType) {
            if (plugins.size() != this.capturedPlugins.size()) {
                return false;
            }
            for (HabboPlugin plugin : this.capturedPlugins) {
                if (!plugins.contains(plugin)) {
                    return false;
                }
            }

            List<RegistrationSource> expectedSources =
                    this.pluginRegistrationSources.getOrDefault(eventType, List.of());
            int sourceCount = 0;

            for (HabboPlugin plugin : this.capturedPlugins) {
                if (plugin == null) {
                    continue;
                }

                Set<Method> registeredMethods;
                synchronized (plugin.registeredEvents) {
                    registeredMethods = plugin.registeredEvents.get(eventType);
                    if (registeredMethods == null) {
                        continue;
                    }

                    RegistrationSource source = null;
                    for (RegistrationSource candidate : expectedSources) {
                        if (candidate.plugin() == plugin) {
                            source = candidate;
                            break;
                        }
                    }
                    if (source == null || !source.methods().equals(registeredMethods)) {
                        return false;
                    }
                }
                sourceCount++;
            }

            return sourceCount == expectedSources.size();
        }

        List<HandlerInvocation> handlersFor(Event event, boolean corrected) {
            Class<? extends Event> eventType = event.getClass().asSubclass(Event.class);
            HandlerLists handlers = this.handlersByEventType.computeIfAbsent(eventType, this::buildHandlerLists);
            return corrected ? handlers.corrected() : handlers.legacy();
        }

        private HandlerLists buildHandlerLists(Class<? extends Event> eventType) {
            List<HandlerInvocation> handlers = new ArrayList<>();
            this.defaultHandlers.stream()
                    .filter(handler -> handler.eventType().isAssignableFrom(eventType))
                    .forEach(handlers::add);
            handlers.addAll(this.pluginHandlers.getOrDefault(eventType, List.of()));
            List<HandlerInvocation> legacy = List.copyOf(handlers);
            handlers.sort(HandlerInvocation.CORRECTED_ORDER);
            return new HandlerLists(legacy, List.copyOf(handlers));
        }
    }

    private record RegistrationSource(HabboPlugin plugin, Set<Method> methods) {}

    private record HandlerLists(List<HandlerInvocation> legacy, List<HandlerInvocation> corrected) {}
}
