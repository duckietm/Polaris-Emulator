package com.eu.habbo.resilience;

import java.util.Objects;

public final class RuntimePressureAssessor {

    private final Thresholds thresholds;

    public RuntimePressureAssessor(Thresholds thresholds) {
        this.thresholds = Objects.requireNonNull(thresholds, "thresholds");
    }

    public RuntimeResilienceController.Pressure assess(Sample sample) {
        Objects.requireNonNull(sample, "sample");
        double peakUtilization = Math.max(
                Math.max(sample.databaseUtilization(), sample.persistenceQueueUtilization()),
                Math.max(
                        Math.max(sample.packetQueueUtilization(), sample.schedulerQueueUtilization()),
                        sample.memoryUtilization()));

        if (sample.criticalDependencyUnavailable()
                || sample.databaseWaiters() >= this.thresholds.criticalDatabaseWaiters()
                || peakUtilization >= this.thresholds.criticalUtilization()) {
            return RuntimeResilienceController.Pressure.CRITICAL;
        }
        if (sample.databaseWaiters() > 0 || peakUtilization >= this.thresholds.degradedUtilization()) {
            return RuntimeResilienceController.Pressure.DEGRADED;
        }
        return RuntimeResilienceController.Pressure.HEALTHY;
    }

    public record Thresholds(double degradedUtilization, double criticalUtilization, int criticalDatabaseWaiters) {
        public Thresholds {
            validateRatio("degradedUtilization", degradedUtilization);
            validateRatio("criticalUtilization", criticalUtilization);
            if (degradedUtilization >= criticalUtilization) {
                throw new IllegalArgumentException("degraded utilization must be below critical utilization");
            }
            if (criticalDatabaseWaiters < 1) {
                throw new IllegalArgumentException("critical database waiters must be positive");
            }
        }
    }

    public record Sample(
            double databaseUtilization,
            int databaseWaiters,
            double persistenceQueueUtilization,
            double packetQueueUtilization,
            double schedulerQueueUtilization,
            double memoryUtilization,
            boolean criticalDependencyUnavailable) {
        public Sample {
            validateRatio("databaseUtilization", databaseUtilization);
            validateRatio("persistenceQueueUtilization", persistenceQueueUtilization);
            validateRatio("packetQueueUtilization", packetQueueUtilization);
            validateRatio("schedulerQueueUtilization", schedulerQueueUtilization);
            validateRatio("memoryUtilization", memoryUtilization);
            if (databaseWaiters < 0) {
                throw new IllegalArgumentException("database waiters must not be negative");
            }
        }
    }

    private static void validateRatio(String name, double value) {
        if (!Double.isFinite(value) || value < 0D || value > 1D) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
