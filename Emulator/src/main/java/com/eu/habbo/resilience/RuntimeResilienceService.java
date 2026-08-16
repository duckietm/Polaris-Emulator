package com.eu.habbo.resilience;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

public final class RuntimeResilienceService {

    private final RuntimeResilienceController controller;
    private final RuntimePressureAssessor assessor;
    private final PressureSource pressureSource;
    private final long samplingIntervalNanos;
    private final LongSupplier nanoTime;
    private final AtomicLong samplingFailures = new AtomicLong();

    private volatile long nextSampleAtNanos;
    private volatile RuntimeResilienceController.Pressure lastPressure = RuntimeResilienceController.Pressure.HEALTHY;
    private volatile String lastSamplingError = "";

    public RuntimeResilienceService(
            RuntimeResilienceController controller,
            RuntimePressureAssessor assessor,
            PressureSource pressureSource,
            long samplingIntervalNanos,
            LongSupplier nanoTime) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.assessor = Objects.requireNonNull(assessor, "assessor");
        this.pressureSource = Objects.requireNonNull(pressureSource, "pressureSource");
        if (samplingIntervalNanos < 1L) {
            throw new IllegalArgumentException("sampling interval must be positive");
        }
        this.samplingIntervalNanos = samplingIntervalNanos;
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
    }

    public RuntimeResilienceController.Admission admit(RuntimeResilienceController.WorkClass workClass) {
        refreshIfDue();
        return this.controller.admit(workClass);
    }

    public Status snapshot() {
        refreshIfDue();
        return new Status(
                this.controller.snapshot(), this.lastPressure, this.samplingFailures.get(), this.lastSamplingError);
    }

    private void refreshIfDue() {
        long now = this.nanoTime.getAsLong();
        if (now < this.nextSampleAtNanos) {
            return;
        }
        synchronized (this) {
            now = this.nanoTime.getAsLong();
            if (now < this.nextSampleAtNanos) {
                return;
            }
            this.nextSampleAtNanos = saturatedAdd(now, this.samplingIntervalNanos);
            try {
                this.lastPressure = this.assessor.assess(this.pressureSource.sample());
                this.lastSamplingError = "";
            } catch (RuntimeException exception) {
                this.samplingFailures.incrementAndGet();
                this.lastPressure = RuntimeResilienceController.Pressure.DEGRADED;
                this.lastSamplingError = exception.getClass().getSimpleName();
            }
            this.controller.record(this.lastPressure);
        }
    }

    private static long saturatedAdd(long value, long increment) {
        if (value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    @FunctionalInterface
    public interface PressureSource {
        RuntimePressureAssessor.Sample sample();
    }

    public record Status(
            RuntimeResilienceController.Snapshot controller,
            RuntimeResilienceController.Pressure lastPressure,
            long samplingFailures,
            String lastSamplingError) {}
}
