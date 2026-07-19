package com.eu.habbo;

import com.eu.habbo.database.Database;
import com.eu.habbo.plugin.events.emulator.EmulatorStartShutdownEvent;
import com.eu.habbo.plugin.events.emulator.EmulatorStoppedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates process teardown in the reverse of the runtime startup phases.
 */
final class RuntimeLifecycle {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(RuntimeLifecycle.class);

    private final PolarisRuntime services;
    private final Runnable sessionCleanup;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();

    RuntimeLifecycle(
            PolarisRuntime services,
            Runnable sessionCleanup) {
        this.services = Objects.requireNonNull(services);
        this.sessionCleanup = Objects.requireNonNull(sessionCleanup);
    }

    void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }

        quiesceRuntime();
        stopNetworkPhase();
        stopHotelPhase();
        stopPluginPhase();
        stopFoundationPhase();
    }

    private void quiesceRuntime() {
        if (services.threading() != null) {
            run(
                    "reject new scheduled work",
                    () -> services.threading().setCanAdd(false));
        }
        if (services.pluginManager() != null) {
            run(
                    "publish pre-shutdown event",
                    () -> services.pluginManager().fireEvent(
                            new EmulatorStartShutdownEvent()));
        }
    }

    private void stopNetworkPhase() {
        if (services.rconServer() != null) {
            run("stop RCON server", () -> services.rconServer().stop());
        }
        if (services.gameServer() != null) {
            run("stop game server", () -> services.gameServer().stop());
        }
        run("dispose resumed sessions", sessionCleanup);
    }

    private void stopHotelPhase() {
        if (services.gameEnvironment() != null) {
            run(
                    "dispose hotel services",
                    () -> services.gameEnvironment().dispose());
        }
    }

    private void stopPluginPhase() {
        if (services.pluginManager() == null) {
            return;
        }

        run(
                "publish hotel-stopped event",
                () -> services.pluginManager().fireEvent(
                        new EmulatorStoppedEvent()));
        run(
                "dispose plugins",
                () -> services.pluginManager().dispose());
    }

    private void stopFoundationPhase() {
        if (services.threading() != null) {
            run(
                    "stop scheduler",
                    () -> services.threading().shutDown());
        }
        run("save configuration", () -> {
            if (canPersistConfiguration()) {
                services.configuration().saveToDatabase();
            }
        });
        if (services.database() != null) {
            run("close database", () -> services.database().dispose());
        }
    }

    private boolean canPersistConfiguration() {
        Database database = services.database();
        return services.configuration() != null
                && database != null
                && database.getDataSource() != null
                && !database.getDataSource().isClosed();
    }

    private void run(String actionName, Runnable action) {
        try {
            action.run();
        } catch (Exception exception) {
            LOGGER.error(
                    "Runtime shutdown action failed: {}",
                    actionName,
                    exception);
        }
    }
}
