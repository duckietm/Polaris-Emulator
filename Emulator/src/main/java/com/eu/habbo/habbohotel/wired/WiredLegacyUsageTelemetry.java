package com.eu.habbo.habbohotel.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.concurrent.atomic.AtomicLongArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bounded operator telemetry for plugin calls that still enter the legacy wired implementation.
 *
 * <p>The tracker deliberately retains no room or plugin references. It logs the first call and
 * power-of-two samples for each operation, which keeps a hot legacy plugin visible without turning
 * wired execution into a logging denial of service.
 */
final class WiredLegacyUsageTelemetry {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredLegacyUsageTelemetry.class);
    private static final AtomicLongArray COUNTS = new AtomicLongArray(Operation.values().length);

    enum Operation {
        HANDLE_TRIGGER_TYPE("handle-trigger-type"),
        HANDLE_CUSTOM_TRIGGER("handle-custom-trigger"),
        HANDLE_TRIGGER_ITEM("handle-trigger-item"),
        EXECUTE_EFFECTS_AT_TILES("execute-effects-at-tiles"),
        DROP_REWARDS("drop-rewards"),
        GET_REWARD("get-reward"),
        RESET_TIMERS("reset-timers");

        private final String label;

        Operation(String label) {
            this.label = label;
        }
    }

    private WiredLegacyUsageTelemetry() {}

    static void record(Operation operation, Room room) {
        long count = COUNTS.incrementAndGet(operation.ordinal());
        if (count == 1 || isPowerOfTwo(count)) {
            LOGGER.warn(
                    "Legacy wired API invoked operation={} room={} count={} action=migrate-to-WiredManager",
                    operation.label,
                    room == null ? 0 : room.getId(),
                    count);
        }
    }

    static long count(Operation operation) {
        return COUNTS.get(operation.ordinal());
    }

    static void resetForTests() {
        for (Operation operation : Operation.values()) {
            COUNTS.set(operation.ordinal(), 0);
        }
    }

    private static boolean isPowerOfTwo(long value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
