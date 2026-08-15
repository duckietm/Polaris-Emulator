package com.eu.habbo.networking.gameserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.monitoring.ExecutionBackpressureMetrics;
import io.netty.util.concurrent.EventExecutorGroup;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class GamePacketExecutionGroup {
    private static final int DEFAULT_QUEUE_CAPACITY = 256;
    private static final Logger LOGGER = LoggerFactory.getLogger(GamePacketExecutionGroup.class);
    private static final int APPLICATION_QUEUE_CAPACITY = configuredQueueCapacity();
    private static final ExecutionCapacityGate ADMISSION_GATE =
            new ExecutionCapacityGate(APPLICATION_QUEUE_CAPACITY, ExecutionBackpressureMetrics.Lane.GAME_PACKET);
    private static final EventExecutorGroup GROUP = BoundedEventExecutorGroups.create(
            configuredThreads(),
            BoundedEventExecutorGroups.withControlTaskReserve(APPLICATION_QUEUE_CAPACITY),
            "GamePacketHandler",
            ExecutionBackpressureMetrics.Lane.GAME_PACKET);

    private GamePacketExecutionGroup() {}

    static EventExecutorGroup get() {
        return GROUP;
    }

    static ExecutionCapacityGate admissionGate() {
        return ADMISSION_GATE;
    }

    static void shutdown() {
        try {
            GROUP.shutdownGracefully(100, 3000, TimeUnit.MILLISECONDS).syncUninterruptibly();
        } catch (Exception e) {
            LOGGER.warn("Packet handler group shutdown interrupted", e);
        }
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

    static int configuredQueueCapacity() {
        ConfigurationManager configuration = Emulator.getConfig();
        int configured = configuration == null
                ? DEFAULT_QUEUE_CAPACITY
                : configuration.getInt("io.packet.handler.queue.capacity", DEFAULT_QUEUE_CAPACITY);
        return BoundedEventExecutorGroups.configuredQueueCapacity(configured, DEFAULT_QUEUE_CAPACITY);
    }
}
