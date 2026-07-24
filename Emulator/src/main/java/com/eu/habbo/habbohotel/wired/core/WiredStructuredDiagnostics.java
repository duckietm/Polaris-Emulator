package com.eu.habbo.habbohotel.wired.core;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in structured execution telemetry containing identifiers only, never WIRED payload text. */
final class WiredStructuredDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredStructuredDiagnostics.class);

    enum Outcome {
        EXECUTED
    }

    @FunctionalInterface
    interface Sink {
        void log(String format, Object... arguments);
    }

    private final BooleanSupplier enabled;
    private final Sink sink;

    WiredStructuredDiagnostics(BooleanSupplier enabled, Sink sink) {
        this.enabled = Objects.requireNonNull(enabled, "enabled");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    static WiredStructuredDiagnostics production() {
        return new WiredStructuredDiagnostics(
                () -> WiredManager.isDebugEnabled() && LOGGER.isDebugEnabled(), LOGGER::debug);
    }

    void execution(
            int roomId,
            int stackItemId,
            WiredEvent.Type triggerType,
            int effectCount,
            long durationMs,
            Outcome outcome) {
        if (!this.enabled.getAsBoolean()) {
            return;
        }

        this.sink.log(
                "wired_execution room={} stack={} trigger={} effects={} duration_ms={} outcome={}",
                Math.max(0, roomId),
                Math.max(0, stackItemId),
                Objects.requireNonNull(triggerType, "triggerType"),
                Math.max(0, effectCount),
                Math.max(0L, durationMs),
                Objects.requireNonNull(outcome, "outcome"));
    }
}
