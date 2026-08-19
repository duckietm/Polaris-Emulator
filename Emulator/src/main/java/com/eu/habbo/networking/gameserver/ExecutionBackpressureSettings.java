package com.eu.habbo.networking.gameserver;

import com.eu.habbo.core.ConfigurationManager;
import java.util.Objects;

record ExecutionBackpressureSettings(
        ExecutionCapacityController.Mode mode,
        int packetCapacity,
        int packetLowWatermark,
        int perConnectionCapacity,
        int perConnectionLowWatermark,
        int perConnectionPendingCapacity,
        long pauseTimeoutMillis,
        int httpCapacity) {
    private static final int DEFAULT_PACKET_CAPACITY = 256;
    private static final int DEFAULT_PER_CONNECTION_CAPACITY = 32;

    static ExecutionBackpressureSettings defaults() {
        return new ExecutionBackpressureSettings(
                ExecutionCapacityController.Mode.OBSERVE, 256, 192, 32, 16, 16, 2_000L, 128);
    }

    static ExecutionBackpressureSettings from(ConfigurationManager configuration) {
        Objects.requireNonNull(configuration, "configuration");

        ExecutionCapacityController.Mode mode = ExecutionCapacityController.Mode.parse(
                configuration.getValue("execution.backpressure.mode", "observe"));
        int packetCapacity = positive(
                configuration.getInt("io.packet.handler.queue.capacity", DEFAULT_PACKET_CAPACITY),
                DEFAULT_PACKET_CAPACITY);
        int packetLowWatermark = lowWatermark(
                configuration.getInt("io.packet.handler.queue.low_watermark", percentage(packetCapacity, 75)),
                packetCapacity,
                percentage(packetCapacity, 75));
        int perConnectionCapacity = positive(
                configuration.getInt("io.packet.handler.per_connection.capacity", DEFAULT_PER_CONNECTION_CAPACITY),
                DEFAULT_PER_CONNECTION_CAPACITY);
        perConnectionCapacity = Math.min(perConnectionCapacity, packetCapacity);
        int perConnectionLowWatermark = lowWatermark(
                configuration.getInt("io.packet.handler.per_connection.low_watermark", perConnectionCapacity / 2),
                perConnectionCapacity,
                perConnectionCapacity / 2);
        int perConnectionPendingCapacity =
                positive(configuration.getInt("io.packet.handler.per_connection.pending", 16), 16);
        long pauseTimeoutMillis =
                positive(configuration.getInt("execution.backpressure.pause.timeout_ms", 2_000), 2_000);
        int httpCapacity = positive(configuration.getInt("http.blocking.queue.capacity", 128), 128);

        return new ExecutionBackpressureSettings(
                mode,
                packetCapacity,
                packetLowWatermark,
                perConnectionCapacity,
                perConnectionLowWatermark,
                perConnectionPendingCapacity,
                pauseTimeoutMillis,
                httpCapacity);
    }

    private static int positive(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    private static int lowWatermark(int configured, int capacity, int fallback) {
        if (configured < 0 || configured >= capacity) {
            return Math.min(Math.max(0, fallback), capacity - 1);
        }
        return configured;
    }

    private static int percentage(int value, int percentage) {
        return Math.max(0, (value * percentage) / 100);
    }
}
