package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GamePacketExecutionGroup {
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketExecutionGroup.class);
    private static final GroupHolder GROUP = new GroupHolder();

    private GamePacketExecutionGroup() {}

    static EventExecutorGroup get() {
        return GROUP.state().executor;
    }

    static ExecutionAdmissionHandler admissionHandler(String targetHandlerName) {
        State state = GROUP.state();
        ExecutionBackpressureSettings settings = state.settings;
        return ExecutionAdmissionHandler.forGamePackets(
                targetHandlerName,
                state.controller,
                state.metrics,
                settings.perConnectionCapacity(),
                settings.perConnectionLowWatermark(),
                settings.perConnectionPendingCapacity(),
                settings.pauseTimeoutMillis());
    }

    static ExecutionCapacityController.Snapshot capacitySnapshot() {
        return GROUP.state().controller.snapshot();
    }

    static ExecutionBackpressureMetrics.Snapshot metricsSnapshot() {
        return GROUP.state().metrics.snapshot();
    }

    static ExecutionBackpressureStatus.Snapshot statusSnapshot() {
        return GROUP.statusSnapshot();
    }

    static void shutdown() {
        GROUP.shutdown();
    }

    static int configuredThreads() {
        int fallback = Math.max(16, Runtime.getRuntime().availableProcessors() * 2);
        ConfigurationManager configuration = Emulator.getConfig();
        if (configuration == null) {
            return fallback;
        }

        int configured = configuration.getInt("io.packet.handler.threads", fallback);
        return configured > 0 ? configured : fallback;
    }

    private static ExecutionBackpressureSettings settings() {
        ConfigurationManager configuration = Emulator.getConfig();
        return configuration == null
                ? ExecutionBackpressureSettings.defaults()
                : ExecutionBackpressureSettings.from(configuration);
    }

    private record State(
            EventExecutorGroup executor,
            ExecutionCapacityController controller,
            ExecutionBackpressureMetrics metrics,
            ExecutionBackpressureSettings settings) {}

    private static final class GroupHolder {
        private State state;

        private synchronized State state() {
            if (this.state == null || this.state.executor.isShuttingDown() || this.state.executor.isShutdown()) {
                ExecutionBackpressureSettings settings = settings();
                ExecutionCapacityController controller = new ExecutionCapacityController(
                        settings.packetCapacity(), settings.packetLowWatermark(), settings.mode());
                ExecutionBackpressureMetrics metrics = new ExecutionBackpressureMetrics("packet");
                EventExecutorGroup executor = BoundedEventExecutorGroups.create(
                        configuredThreads(), settings.packetCapacity(), "GamePacketHandler", settings.mode());
                this.state = new State(executor, controller, metrics, settings);
            }
            return this.state;
        }

        private synchronized void shutdown() {
            if (this.state == null) {
                return;
            }
            try {
                this.state
                        .executor
                        .shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS)
                        .syncUninterruptibly();
            } catch (Exception e) {
                LOGGER.warn("Packet handler group shutdown interrupted", e);
            } finally {
                this.state = null;
            }
        }

        private synchronized ExecutionBackpressureStatus.Snapshot statusSnapshot() {
            if (this.state == null) {
                return ExecutionBackpressureStatus.Snapshot.inactive();
            }
            return ExecutionBackpressureStatus.combine(this.state.controller.snapshot(), this.state.metrics.snapshot());
        }
    }
}
