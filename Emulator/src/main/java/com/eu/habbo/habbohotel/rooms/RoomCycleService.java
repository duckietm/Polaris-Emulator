package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.core.ConfigurationManager;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs the room cycles (walking, rollers, furni states, pets and bots) on their own workers instead of
 * the shared game pool. A room always runs on the same worker, picked by its id, so a room that lags
 * only holds up the rooms on its worker, never the whole hotel or the other server tasks.
 */
public final class RoomCycleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomCycleService.class);

    public static final String WORKERS_SETTING = "room.cycle.workers";
    public static final String SLOW_MS_SETTING = "room.cycle.slow_ms";
    public static final int CYCLE_MS = 500;
    public static final int MAX_WORKERS = 64;
    public static final int DEFAULT_SLOW_MS = 250;
    private static final long SLOW_WARNING_INTERVAL_MS = 30_000L;
    private static final int MAX_SLOW_WARNING_KEYS = 10_000;

    private final ScheduledExecutorService[] workers;
    private final long slowNanos;
    private final LongSupplier nanoTime;
    private final ConcurrentHashMap<Integer, Long> lastSlowWarning = new ConcurrentHashMap<>();

    public RoomCycleService(int workerCount, int slowMs) {
        this(workerCount, slowMs, System::nanoTime);
    }

    RoomCycleService(int workerCount, int slowMs, LongSupplier nanoTime) {
        int count = Math.max(1, Math.min(MAX_WORKERS, workerCount));
        this.workers = new ScheduledExecutorService[count];
        for (int i = 0; i < count; i++) {
            ScheduledThreadPoolExecutor executor =
                    new ScheduledThreadPoolExecutor(1, new DefaultThreadFactory("RoomCycle-" + i, true));
            executor.setRemoveOnCancelPolicy(true);
            this.workers[i] = executor;
        }
        this.slowNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(1, slowMs));
        this.nanoTime = nanoTime;
        LOGGER.info("Room cycles -> {} workers", count);
    }

    public static RoomCycleService fromConfig(ConfigurationManager config) {
        int workers = config == null ? 0 : config.getInt(WORKERS_SETTING, 0);
        int slowMs = config == null ? DEFAULT_SLOW_MS : config.getInt(SLOW_MS_SETTING, DEFAULT_SLOW_MS);
        return new RoomCycleService(workers > 0 ? workers : defaultWorkers(), slowMs);
    }

    /** 0 in the settings: one worker per processor, at least 2 and at most 16. */
    static int defaultWorkers() {
        return Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors()));
    }

    public int workerCount() {
        return this.workers.length;
    }

    int workerOf(int roomId) {
        return Math.floorMod(roomId, this.workers.length);
    }

    /** Starts the room's cycle every {@link #CYCLE_MS} on its worker; cancel the future to stop it. */
    public ScheduledFuture<?> schedule(Room room) {
        return this.workers[this.workerOf(room.getId())].scheduleAtFixedRate(
                () -> this.runCycle(room), CYCLE_MS, CYCLE_MS, TimeUnit.MILLISECONDS);
    }

    /** One cycle. Nothing may escape: a fixed-rate task that throws is never run again. */
    void runCycle(Room room) {
        long started = this.nanoTime.getAsLong();
        try {
            room.run();
        } catch (VirtualMachineError error) {
            if (!(error instanceof StackOverflowError)) {
                throw error;
            }
            LOGGER.error("Room {} cycle overflowed its stack", room.getId(), error);
        } catch (Throwable throwable) {
            LOGGER.error("Room {} cycle failed", room.getId(), throwable);
        }

        long took = this.nanoTime.getAsLong() - started;
        if (took > this.slowNanos) {
            this.warnSlow(room, took);
        }
    }

    private void warnSlow(Room room, long tookNanos) {
        long nowMs = TimeUnit.NANOSECONDS.toMillis(this.nanoTime.getAsLong());
        Long last = this.lastSlowWarning.get(room.getId());
        if (last != null && nowMs - last < SLOW_WARNING_INTERVAL_MS) {
            return;
        }
        if (this.lastSlowWarning.size() >= MAX_SLOW_WARNING_KEYS) {
            this.lastSlowWarning.clear();
        }
        this.lastSlowWarning.put(room.getId(), nowMs);
        LOGGER.warn(
                "Room {} cycle took {} ms on worker {}; rooms on the same worker wait for it",
                room.getId(),
                TimeUnit.NANOSECONDS.toMillis(tookNanos),
                this.workerOf(room.getId()));
    }

    public void dispose() {
        for (ScheduledExecutorService worker : this.workers) {
            worker.shutdown();
        }
        for (ScheduledExecutorService worker : this.workers) {
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                worker.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        LOGGER.info("Room cycles -> Disposed!");
    }
}
