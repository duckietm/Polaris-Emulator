package com.eu.habbo;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.threading.ThreadPooling;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Composition boundary for legacy WIRED classes whose public no-argument construction contract
 * prevents constructor injection. New internal collaborators should still prefer constructors.
 */
public final class WiredPlatform {
    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();

    private WiredPlatform() {}

    static void install(Services services) {
        if (!SERVICES.compareAndSet(null, Objects.requireNonNull(services, "services"))) {
            throw new IllegalStateException("WIRED platform services already installed");
        }
    }

    public static ConfigurationManager configuration() {
        Services services = SERVICES.get();
        return services == null ? null : services.configuration().get();
    }

    public static Database database() {
        Services services = SERVICES.get();
        return services == null ? null : services.database().get();
    }

    public static GameEnvironment gameEnvironment() {
        Services services = SERVICES.get();
        return services == null ? null : services.gameEnvironment().get();
    }

    public static ThreadPooling threading() {
        Services services = SERVICES.get();
        return services == null ? null : services.threading().get();
    }

    public static PluginManager pluginManager() {
        Services services = SERVICES.get();
        return services == null ? null : services.pluginManager().get();
    }

    public static Random random() {
        Services services = SERVICES.get();
        return services == null ? null : services.random().get();
    }

    public static int unixTimestamp() {
        Services services = SERVICES.get();
        return services == null ? 0 : services.unixTimestamp().getAsInt();
    }

    public static boolean isReady() {
        Services services = SERVICES.get();
        return services != null && services.ready().getAsBoolean();
    }

    record Services(
            Supplier<ConfigurationManager> configuration,
            Supplier<Database> database,
            Supplier<GameEnvironment> gameEnvironment,
            Supplier<ThreadPooling> threading,
            Supplier<PluginManager> pluginManager,
            Supplier<Random> random,
            IntSupplier unixTimestamp,
            BooleanSupplier ready) {
        Services {
            Objects.requireNonNull(configuration, "configuration");
            Objects.requireNonNull(database, "database");
            Objects.requireNonNull(gameEnvironment, "gameEnvironment");
            Objects.requireNonNull(threading, "threading");
            Objects.requireNonNull(pluginManager, "pluginManager");
            Objects.requireNonNull(random, "random");
            Objects.requireNonNull(unixTimestamp, "unixTimestamp");
            Objects.requireNonNull(ready, "ready");
        }
    }
}
