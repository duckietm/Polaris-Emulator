package com.eu.habbo.resilience;

import com.eu.habbo.core.ConfigurationManager;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public record RuntimeResilienceSettings(
        RuntimeResilienceController.Mode mode,
        long sampleIntervalMillis,
        double degradedUtilization,
        double criticalUtilization,
        int criticalDatabaseWaiters,
        int degradedWindows,
        int criticalWindows,
        int recoveryWindows,
        int schedulerCriticalQueue,
        DependencyCircuitBreakers.Settings circuitBreaker) {

    public static RuntimeResilienceSettings from(ConfigurationManager configuration) {
        Objects.requireNonNull(configuration, "configuration");
        RuntimeResilienceController.Mode mode = "enforce"
                        .equals(configuration
                                .getValue("runtime.resilience.mode", "observe")
                                .trim()
                                .toLowerCase(Locale.ROOT))
                ? RuntimeResilienceController.Mode.ENFORCE
                : RuntimeResilienceController.Mode.OBSERVE;

        int degradedPercent = configuration.getInt("runtime.resilience.degraded.percent", 75);
        int criticalPercent = configuration.getInt("runtime.resilience.critical.percent", 95);
        if (degradedPercent < 1 || criticalPercent > 100 || degradedPercent >= criticalPercent) {
            degradedPercent = 75;
            criticalPercent = 95;
        }

        int circuitWindow = positive(configuration.getInt("runtime.resilience.circuit.window", 20), 20);
        int circuitMinimumCalls = Math.min(
                circuitWindow, positive(configuration.getInt("runtime.resilience.circuit.minimum_calls", 10), 10));

        return new RuntimeResilienceSettings(
                mode,
                positive(configuration.getInt("runtime.resilience.sample.interval_ms", 1_000), 1_000),
                degradedPercent / 100D,
                criticalPercent / 100D,
                positive(configuration.getInt("runtime.resilience.database.critical_waiters", 2), 2),
                positive(configuration.getInt("runtime.resilience.degraded.windows", 3), 3),
                positive(configuration.getInt("runtime.resilience.critical.windows", 2), 2),
                positive(configuration.getInt("runtime.resilience.recovery.windows", 10), 10),
                positive(configuration.getInt("runtime.resilience.scheduler.critical_queue", 10_000), 10_000),
                new DependencyCircuitBreakers.Settings(
                        percentage(configuration.getInt("runtime.resilience.circuit.failure_percent", 50), 50),
                        circuitWindow,
                        circuitMinimumCalls,
                        Duration.ofMillis(
                                positive(configuration.getInt("runtime.resilience.circuit.open_ms", 30_000), 30_000)),
                        positive(configuration.getInt("runtime.resilience.circuit.half_open_calls", 3), 3)));
    }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private static float percentage(int value, int fallback) {
        return value > 0 && value <= 100 ? value : fallback;
    }
}
