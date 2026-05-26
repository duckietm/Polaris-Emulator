package com.eu.habbo.habbohotel.wired.tick;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Centralized tick service for all wired timing operations.
 *
 * <p>This version keeps a single global tick clock, but distributes room processing
 * across multiple single-threaded shard workers. A room is always processed on the
 * same shard, preserving in-room order while preventing one heavy room from delaying
 * all other rooms.</p>
 */
public final class WiredTickService {

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredTickService.class);

    public static final int DEFAULT_TICK_INTERVAL_MS = 50;
    public static final int MIN_TICK_INTERVAL_MS = 10;
    public static final int MAX_TICK_INTERVAL_MS = 500;

    public static final int DEFAULT_WORKER_COUNT = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    public static final int MIN_WORKER_COUNT = 1;
    public static final int MAX_WORKER_COUNT = 32;

    public static final long SLOW_TICKABLE_THRESHOLD_MS = 100L;
    public static final long SLOW_ROOM_THRESHOLD_MS = 50L;
    public static final long SLOW_SHARD_THRESHOLD_MS = 250L;

    private static volatile WiredTickService instance;

    private int tickIntervalMs = DEFAULT_TICK_INTERVAL_MS;
    private boolean debugEnabled = false;
    private int threadPriority = Thread.NORM_PRIORITY + 1;
    private int workerCount = DEFAULT_WORKER_COUNT;

    /** Global logical tick counter shared by every shard. */
    private final AtomicLong tickCount = new AtomicLong(0);

    /** Schedules the global logical ticks. */
    private ScheduledExecutorService coordinator;

    /** One single-thread executor per shard, preserving order inside the shard. */
    private ExecutorService[] shardExecutors;

    /** Highest logical tick requested for each shard. */
    private AtomicLong[] shardRequestedTicks;

    /** Last logical tick fully processed by each shard. */
    private AtomicLong[] shardProcessedTicks;

    /** Whether a shard worker loop is currently scheduled/running. */
    private AtomicBoolean[] shardScheduled;

    private final ConcurrentHashMap<Integer, Set<WiredTickable>>[] shardRoomTickables;
    private final AtomicBoolean running;

    @SuppressWarnings("unchecked")
    private WiredTickService() {
        this.shardRoomTickables = new ConcurrentHashMap[MAX_WORKER_COUNT];
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            this.shardRoomTickables[i] = new ConcurrentHashMap<>();
        }
        this.running = new AtomicBoolean(false);
    }

    private void loadConfiguration() {
        int configuredInterval = Emulator.getConfig().getInt("wired.tick.interval.ms", DEFAULT_TICK_INTERVAL_MS);
        this.tickIntervalMs = Math.max(MIN_TICK_INTERVAL_MS, Math.min(MAX_TICK_INTERVAL_MS, configuredInterval));

        if (configuredInterval != this.tickIntervalMs) {
            LOGGER.warn(
                    "wired.tick.interval.ms value {} is out of range [{}-{}], using {}",
                    configuredInterval,
                    MIN_TICK_INTERVAL_MS,
                    MAX_TICK_INTERVAL_MS,
                    this.tickIntervalMs
            );
        }

        this.debugEnabled = Emulator.getConfig().getBoolean("wired.tick.debug", false);

        int configuredPriority = Emulator.getConfig().getInt("wired.tick.thread.priority", Thread.NORM_PRIORITY + 1);
        this.threadPriority = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, configuredPriority));

        int configuredWorkers = Emulator.getConfig().getInt("wired.tick.workers", DEFAULT_WORKER_COUNT);
        this.workerCount = Math.max(MIN_WORKER_COUNT, Math.min(MAX_WORKER_COUNT, configuredWorkers));

        if (configuredWorkers != this.workerCount) {
            LOGGER.warn(
                    "wired.tick.workers value {} is out of range [{}-{}], using {}",
                    configuredWorkers,
                    MIN_WORKER_COUNT,
                    MAX_WORKER_COUNT,
                    this.workerCount
            );
        }
    }

    public int getTickIntervalMs() {
        return tickIntervalMs;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public int getWorkerCount() {
        return workerCount;
    }

    public static WiredTickService getInstance() {
        if (instance == null) {
            synchronized (WiredTickService.class) {
                if (instance == null) {
                    instance = new WiredTickService();
                }
            }
        }
        return instance;
    }

    public synchronized void start() {
        if (running.get()) {
            LOGGER.warn("WiredTickService already running");
            return;
        }

        loadConfiguration();

        LOGGER.info(
                "Starting WiredTickService with {}ms tick interval (workers={}, debug={}, priority={})...",
                tickIntervalMs,
                workerCount,
                debugEnabled,
                threadPriority
        );

        this.coordinator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WiredTickCoordinator");
            t.setDaemon(true);
            t.setPriority(threadPriority);
            return t;
        });

        this.shardExecutors = new ExecutorService[workerCount];
        this.shardRequestedTicks = new AtomicLong[workerCount];
        this.shardProcessedTicks = new AtomicLong[workerCount];
        this.shardScheduled = new AtomicBoolean[workerCount];

        for (int i = 0; i < workerCount; i++) {
            final int shardIndex = i;
            this.shardExecutors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "WiredTickShard-" + shardIndex);
                t.setDaemon(true);
                t.setPriority(threadPriority);
                return t;
            });
            this.shardRequestedTicks[i] = new AtomicLong(0L);
            this.shardProcessedTicks[i] = new AtomicLong(0L);
            this.shardScheduled[i] = new AtomicBoolean(false);
        }

        this.tickCount.set(0L);
        running.set(true);

        this.coordinator.scheduleAtFixedRate(
                () -> {
                    try {
                        dispatchTick();
                    } catch (Throwable t) {
                        LOGGER.error("WiredTickService fatal coordinator error", t);
                    }
                },
                tickIntervalMs,
                tickIntervalMs,
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("WiredTickService started successfully");
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        LOGGER.info("Stopping WiredTickService...");
        running.set(false);

        if (coordinator != null) {
            coordinator.shutdown();
            try {
                if (!coordinator.awaitTermination(5, TimeUnit.SECONDS)) {
                    coordinator.shutdownNow();
                }
            } catch (InterruptedException e) {
                coordinator.shutdownNow();
                Thread.currentThread().interrupt();
            }
            coordinator = null;
        }

        if (shardExecutors != null) {
            for (ExecutorService executor : shardExecutors) {
                if (executor != null) {
                    executor.shutdown();
                }
            }

            for (ExecutorService executor : shardExecutors) {
                if (executor == null) {
                    continue;
                }
                try {
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }

        shardExecutors = null;
        shardRequestedTicks = null;
        shardProcessedTicks = null;
        shardScheduled = null;

        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            shardRoomTickables[i].clear();
        }
        LOGGER.info("WiredTickService stopped");
    }

    public boolean isRunning() {
        return running.get();
    }

    public void register(Room room, WiredTickable tickable) {
        if (room == null || tickable == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());

        if (tickables.add(tickable)) {
            tickable.onRegistered(room, System.currentTimeMillis());
        }
    }

    public void unregister(Room room, WiredTickable tickable) {
        if (room == null || tickable == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            if (tickables.remove(tickable)) {
                tickable.onUnregistered(room);
            }

            if (tickables.isEmpty()) {
                shardRoomTickables[shardIndex].remove(roomId);
            }
        }
    }

    public void unregister(int roomId, int tickableId) {
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            tickables.removeIf(t -> {
                if (t.getId() == tickableId) {
                    Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
                    if (room != null) {
                        t.onUnregistered(room);
                    }
                    return true;
                }
                return false;
            });

            if (tickables.isEmpty()) {
                shardRoomTickables[shardIndex].remove(roomId);
            }
        }
    }

    public void unregisterRoom(Room room) {
        if (room == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].remove(roomId);

        if (tickables != null) {
            for (WiredTickable tickable : tickables) {
                try {
                    if (tickable != null) {
                        tickable.onUnregistered(room);
                    }
                } catch (Throwable t) {
                    LOGGER.error(
                            "Error unregistering tickable {} from room {}",
                            tickable != null ? tickable.getId() : -1,
                            room.getId(),
                            t
                    );
                }
            }
            LOGGER.debug("Unregistered {} tickables from room {}", tickables.size(), room.getId());
        }
    }

    public void resetRoomTimers(Room room) {
        if (room == null) {
            return;
        }

        int roomId = room.getId();
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);

        if (tickables != null) {
            for (WiredTickable tickable : tickables) {
                try {
                    if (tickable != null) {
                        tickable.resetTimer();
                    }
                } catch (Throwable e) {
                    LOGGER.error(
                            "Error resetting timer for tickable {} in room {}",
                            tickable != null ? tickable.getId() : -1,
                            room.getId(),
                            e
                    );
                }
            }
        }
    }

    public int getTickableCount(int roomId) {
        int shardIndex = getShardIndex(roomId);
        Set<WiredTickable> tickables = shardRoomTickables[shardIndex].get(roomId);
        return tickables != null ? tickables.size() : 0;
    }

    public int getTotalTickableCount() {
        int count = 0;
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            count += shardRoomTickables[i].values().stream().mapToInt(Set::size).sum();
        }
        return count;
    }

    public int getActiveRoomCount() {
        int count = 0;
        for (int i = 0; i < MAX_WORKER_COUNT; i++) {
            count += shardRoomTickables[i].size();
        }
        return count;
    }

    public long getTickCount() {
        return tickCount.get();
    }

    private void dispatchTick() {
        if (!running.get() || Emulator.isShuttingDown) {
            return;
        }

        long currentTick = tickCount.incrementAndGet();

        for (int shardIndex = 0; shardIndex < workerCount; shardIndex++) {
            shardRequestedTicks[shardIndex].set(currentTick);
            scheduleShardIfNeeded(shardIndex);
        }
    }

    private void scheduleShardIfNeeded(int shardIndex) {
        if (!running.get() || shardExecutors == null) {
            return;
        }

        if (shardScheduled[shardIndex].compareAndSet(false, true)) {
            shardExecutors[shardIndex].execute(() -> runShardLoop(shardIndex));
        }
    }

    private void runShardLoop(int shardIndex) {
        try {
            while (running.get() && !Emulator.isShuttingDown) {
                long nextTick = shardProcessedTicks[shardIndex].get() + 1L;
                long requestedTick = shardRequestedTicks[shardIndex].get();

                if (nextTick > requestedTick) {
                    break;
                }

                // If lagging by more than 5 ticks (250ms), skip intermediate ticks to avoid CPU starvation
                if (requestedTick - nextTick > 5) {
                    nextTick = requestedTick - 5;
                    shardProcessedTicks[shardIndex].set(nextTick);
                }

                processShardTick(shardIndex, nextTick);
                shardProcessedTicks[shardIndex].set(nextTick);
            }
        } catch (Throwable t) {
            LOGGER.error("Fatal error in WiredTick shard {}", shardIndex, t);
        } finally {
            shardScheduled[shardIndex].set(false);
            if (running.get() && shardProcessedTicks[shardIndex].get() < shardRequestedTicks[shardIndex].get()) {
                scheduleShardIfNeeded(shardIndex);
            }
        }
    }

    private void processShardTick(int shardIndex, long currentTick) {
        long shardStart = System.currentTimeMillis();
        int processedTickables = 0;
        int processedRooms = 0;

        for (Map.Entry<Integer, Set<WiredTickable>> entry : shardRoomTickables[shardIndex].entrySet()) {
            int roomId = entry.getKey();
            Set<WiredTickable> tickables = entry.getValue();
            if (tickables == null || tickables.isEmpty()) {
                continue;
            }

            Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
            if (room == null || !room.isLoaded()) {
                continue;
            }

            if (room.getCurrentHabbos().isEmpty() && room.getCurrentBots().isEmpty()) {
                continue;
            }

            long roomStart = System.currentTimeMillis();
            processedRooms++;

            for (WiredTickable tickable : tickables) {
                long tickableStart = System.currentTimeMillis();

                if (tickable == null) {
                    continue;
                }

                try {
                    if (tickable.getRoomId() != roomId) {
                        unregister(roomId, tickable.getId());
                        continue;
                    }

                    tickable.onWiredTick(room, currentTick, tickIntervalMs);
                    processedTickables++;

                    long tickableDuration = System.currentTimeMillis() - tickableStart;
                    if (tickableDuration > SLOW_TICKABLE_THRESHOLD_MS) {
                        LOGGER.warn(
                                "Slow wired tickable: shard={}, room={}, tick={}, tickableId={}, class={}, took={}ms",
                                shardIndex,
                                roomId,
                                currentTick,
                                tickable.getId(),
                                tickable.getClass().getName(),
                                tickableDuration
                        );
                    }
                } catch (Throwable t) {
                    long tickableDuration = System.currentTimeMillis() - tickableStart;
                    LOGGER.error(
                            "Error in wired tick for tickable {} in room {} after {}ms",
                            tickable.getId(),
                            roomId,
                            tickableDuration,
                            t
                    );
                }
            }

            long roomDuration = System.currentTimeMillis() - roomStart;
            if (roomDuration > SLOW_ROOM_THRESHOLD_MS) {
                LOGGER.warn(
                        "Slow wired room tick: shard={}, room={}, tick={}, tickables={}, took={}ms",
                        shardIndex,
                        roomId,
                        currentTick,
                        tickables.size(),
                        roomDuration
                );
            }
        }

        long shardDuration = System.currentTimeMillis() - shardStart;
        if (shardDuration > SLOW_SHARD_THRESHOLD_MS) {
            LOGGER.warn(
                    "Slow wired shard tick: shard={}, tick={}, rooms={}, tickables={}, took={}ms",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration
            );
        }

        if (debugEnabled && processedTickables > 0) {
            LOGGER.debug(
                    "Wired shard tick completed: shard={}, tick={}, rooms={}, tickables={}, took={}ms",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration
            );
        }
    }

    private int getShardIndex(int roomId) {
        return Math.floorMod(roomId, workerCount);
    }
}
