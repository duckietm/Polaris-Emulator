package com.eu.habbo.resilience;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.database.PersistenceExecutor;
import com.eu.habbo.networking.gameserver.ExecutionBackpressureStatus;
import com.eu.habbo.threading.ThreadPooling;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RuntimeResilienceRuntime {

    private static final AtomicReference<RuntimeState> STATE =
            new AtomicReference<>(new RuntimeState(inactiveService(), inactiveBreakers()));

    private RuntimeResilienceRuntime() {}

    public static void install(
            ConfigurationManager configuration,
            Database database,
            PersistenceExecutor persistenceExecutor,
            ThreadPooling threading) {
        RuntimeResilienceSettings settings = RuntimeResilienceSettings.from(configuration);
        RuntimePressureSampler sampler = new RuntimePressureSampler(
                () -> databaseReading(database),
                () -> persistenceReading(persistenceExecutor),
                RuntimeResilienceRuntime::packetReading,
                () -> schedulerReading(threading, settings.schedulerCriticalQueue()),
                RuntimeResilienceRuntime::memoryUtilization);
        RuntimeResilienceController controller = new RuntimeResilienceController(
                settings.mode(),
                new RuntimeResilienceController.Thresholds(
                        settings.degradedWindows(), settings.criticalWindows(), settings.recoveryWindows()));
        RuntimePressureAssessor assessor = new RuntimePressureAssessor(new RuntimePressureAssessor.Thresholds(
                settings.degradedUtilization(), settings.criticalUtilization(), settings.criticalDatabaseWaiters()));
        install(
                new RuntimeResilienceService(
                        controller,
                        assessor,
                        sampler,
                        TimeUnit.MILLISECONDS.toNanos(settings.sampleIntervalMillis()),
                        System::nanoTime),
                new DependencyCircuitBreakers(settings.mode(), settings.circuitBreaker()));
    }

    public static RuntimeResilienceController.Admission admit(RuntimeResilienceController.WorkClass workClass) {
        return STATE.get().service().admit(workClass);
    }

    public static RuntimeResilienceService.Status status() {
        return STATE.get().service().snapshot();
    }

    public static <T> T executeExternal(
            String dependency,
            DependencyCircuitBreakers.CheckedSupplier<T> primary,
            DependencyCircuitBreakers.CheckedSupplier<T> fallback) {
        return STATE.get().circuitBreakers().execute(dependency, primary, fallback);
    }

    public static DependencyCircuitBreakers.Snapshot circuitSnapshot(String dependency) {
        return STATE.get().circuitBreakers().snapshot(dependency);
    }

    static void install(RuntimeResilienceService newService, DependencyCircuitBreakers newCircuitBreakers) {
        STATE.set(new RuntimeState(
                Objects.requireNonNull(newService, "service"),
                Objects.requireNonNull(newCircuitBreakers, "circuitBreakers")));
    }

    static void resetForTests() {
        STATE.set(new RuntimeState(inactiveService(), inactiveBreakers()));
    }

    private static RuntimePressureSampler.DatabaseReading databaseReading(Database database) {
        HikariDataSource dataSource = database == null ? null : database.getDataSource();
        HikariPoolMXBean pool = dataSource == null ? null : dataSource.getHikariPoolMXBean();
        if (dataSource == null || dataSource.isClosed() || pool == null) {
            return new RuntimePressureSampler.DatabaseReading(0, 0, 0, true);
        }
        return new RuntimePressureSampler.DatabaseReading(
                pool.getActiveConnections(),
                dataSource.getMaximumPoolSize(),
                pool.getThreadsAwaitingConnection(),
                false);
    }

    private static RuntimePressureSampler.QueueReading persistenceReading(PersistenceExecutor executor) {
        return executor == null
                ? new RuntimePressureSampler.QueueReading(0, 0)
                : new RuntimePressureSampler.QueueReading(executor.getQueueDepth(), executor.getQueueCapacity());
    }

    private static RuntimePressureSampler.QueueReading packetReading() {
        ExecutionBackpressureStatus.Snapshot snapshot = ExecutionBackpressureStatus.packet();
        return new RuntimePressureSampler.QueueReading(snapshot.inFlight(), snapshot.capacity());
    }

    private static RuntimePressureSampler.QueueReading schedulerReading(ThreadPooling threading, int capacity) {
        if (threading == null || !(threading.getService() instanceof ScheduledThreadPoolExecutor executor)) {
            return new RuntimePressureSampler.QueueReading(0, capacity);
        }
        return new RuntimePressureSampler.QueueReading(executor.getQueue().size(), capacity);
    }

    private static double memoryUtilization() {
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        return max <= 0L ? 0D : (double) used / max;
    }

    private static RuntimeResilienceService inactiveService() {
        return new RuntimeResilienceService(
                new RuntimeResilienceController(
                        RuntimeResilienceController.Mode.OBSERVE, new RuntimeResilienceController.Thresholds(1, 1, 1)),
                new RuntimePressureAssessor(new RuntimePressureAssessor.Thresholds(0.75D, 0.95D, 2)),
                () -> new RuntimePressureAssessor.Sample(0D, 0, 0D, 0D, 0D, 0D, false),
                Long.MAX_VALUE,
                System::nanoTime);
    }

    private static DependencyCircuitBreakers inactiveBreakers() {
        return new DependencyCircuitBreakers(
                RuntimeResilienceController.Mode.OBSERVE,
                new DependencyCircuitBreakers.Settings(50F, 2, 2, Duration.ofSeconds(30), 1));
    }

    private record RuntimeState(RuntimeResilienceService service, DependencyCircuitBreakers circuitBreakers) {}
}
