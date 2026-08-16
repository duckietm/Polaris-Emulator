package com.eu.habbo.resilience;

import java.util.Objects;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

public final class RuntimePressureSampler implements RuntimeResilienceService.PressureSource {

    private final Supplier<DatabaseReading> database;
    private final Supplier<QueueReading> persistence;
    private final Supplier<QueueReading> packets;
    private final Supplier<QueueReading> scheduler;
    private final DoubleSupplier memoryUtilization;

    public RuntimePressureSampler(
            Supplier<DatabaseReading> database,
            Supplier<QueueReading> persistence,
            Supplier<QueueReading> packets,
            Supplier<QueueReading> scheduler,
            DoubleSupplier memoryUtilization) {
        this.database = Objects.requireNonNull(database, "database");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.packets = Objects.requireNonNull(packets, "packets");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.memoryUtilization = Objects.requireNonNull(memoryUtilization, "memoryUtilization");
    }

    @Override
    public RuntimePressureAssessor.Sample sample() {
        DatabaseReading databaseReading = Objects.requireNonNull(this.database.get(), "database reading");
        return new RuntimePressureAssessor.Sample(
                ratio(databaseReading.active(), databaseReading.capacity()),
                Math.max(0, databaseReading.waiters()),
                ratio(this.persistence.get()),
                ratio(this.packets.get()),
                ratio(this.scheduler.get()),
                clamp(this.memoryUtilization.getAsDouble()),
                databaseReading.unavailable());
    }

    private static double ratio(QueueReading reading) {
        Objects.requireNonNull(reading, "queue reading");
        return ratio(reading.depth(), reading.capacity());
    }

    private static double ratio(int value, int capacity) {
        return capacity <= 0 ? 0D : clamp((double) Math.max(0, value) / capacity);
    }

    private static double clamp(double value) {
        if (!Double.isFinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }

    public record DatabaseReading(int active, int capacity, int waiters, boolean unavailable) {}

    public record QueueReading(int depth, int capacity) {}
}
