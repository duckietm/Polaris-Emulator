package com.eu.habbo.habbohotel.wired.tick;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.rooms.HeavyWiredRooms;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public static final int DEFAULT_WORKER_COUNT =
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
    public static final int MIN_WORKER_COUNT = 1;
    public static final int MAX_WORKER_COUNT = 32;

    /** Extra workers for rooms the wired monitor marked heavy; 0 keeps them on their usual worker. */
    static final int DEFAULT_HEAVY_WORKER_COUNT = 1;

    static final int MAX_HEAVY_WORKER_COUNT = 8;

    public static final long SLOW_TICKABLE_THRESHOLD_MS = 100L;
    /** A slow room warns at most this often: at 20 ticks a second it used to flood the log. */
    private static final long SLOW_WARNING_INTERVAL_MS = 10_000L;

    private static final int MAX_SLOW_WARNING_KEYS = 10_000;
    private final ConcurrentHashMap<Long, Long> lastSlowWarningMs = new ConcurrentHashMap<>();
    public static final long SLOW_ROOM_THRESHOLD_MS = 50L;
    public static final long SLOW_SHARD_THRESHOLD_MS = 250L;

    private static volatile WiredTickService instance;

    /** At most this many room tasks (signal chains) wait per room; more are dropped. */
    public static final int MAX_PENDING_ROOM_TASKS = 2_000;

    /** The shard the current thread works for, so a room's own worker runs its tasks inline. */
    private static final ThreadLocal<Integer> CURRENT_SHARD = new ThreadLocal<>();

    private final ConcurrentHashMap<Integer, java.util.concurrent.atomic.AtomicInteger> pendingRoomTasks =
            new ConcurrentHashMap<>();

    private int tickIntervalMs = DEFAULT_TICK_INTERVAL_MS;
    private boolean debugEnabled = false;
    private int threadPriority = Thread.NORM_PRIORITY + 1;
    private int workerCount = DEFAULT_WORKER_COUNT;
    private int heavyWorkerCount = DEFAULT_HEAVY_WORKER_COUNT;

    /** The shard each room with tickables is on; a heavy room moves to a heavy shard and back. */
    private final ConcurrentHashMap<Integer, Integer> roomShards = new ConcurrentHashMap<>();

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
    private boolean fixedConfiguration;

    @SuppressWarnings("unchecked")
    private WiredTickService() {
        this.shardRoomTickables = new ConcurrentHashMap[MAX_WORKER_COUNT + MAX_HEAVY_WORKER_COUNT];
        for (int i = 0; i < this.shardRoomTickables.length; i++) {
            this.shardRoomTickables[i] = new ConcurrentHashMap<>();
        }
        this.running = new AtomicBoolean(false);
    }

    /** For tests: fixed settings, no hotel configuration read on start. */
    WiredTickService(int workerCount, int tickIntervalMs) {
        this(workerCount, tickIntervalMs, 0);
    }

    /** For tests: fixed settings, with heavy workers. */
    WiredTickService(int workerCount, int tickIntervalMs, int heavyWorkerCount) {
        this();
        this.workerCount = Math.max(MIN_WORKER_COUNT, Math.min(MAX_WORKER_COUNT, workerCount));
        this.tickIntervalMs = Math.max(MIN_TICK_INTERVAL_MS, Math.min(MAX_TICK_INTERVAL_MS, tickIntervalMs));
        this.heavyWorkerCount = Math.max(0, Math.min(MAX_HEAVY_WORKER_COUNT, heavyWorkerCount));
        this.fixedConfiguration = true;
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
                    this.tickIntervalMs);
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
                    this.workerCount);
        }

        ConfigurationManager config = WiredPlatform.configuration();
        int configuredHeavy = config != null
                ? config.getInt("wired.tick.heavy.workers", DEFAULT_HEAVY_WORKER_COUNT)
                : DEFAULT_HEAVY_WORKER_COUNT;
        this.heavyWorkerCount = Math.max(0, Math.min(MAX_HEAVY_WORKER_COUNT, configuredHeavy));
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

    int getHeavyWorkerCount() {
        return heavyWorkerCount;
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

        if (!this.fixedConfiguration) {
            loadConfiguration();
        }

        LOGGER.info(
                "Starting WiredTickService with {}ms tick interval (workers={}, heavy workers={}, debug={}, priority={})...",
                tickIntervalMs,
                workerCount,
                heavyWorkerCount,
                debugEnabled,
                threadPriority);

        this.coordinator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WiredTickCoordinator");
            t.setDaemon(true);
            t.setPriority(threadPriority);
            return t;
        });

        int shards = totalShards();
        this.shardExecutors = new ExecutorService[shards];
        this.shardRequestedTicks = new AtomicLong[shards];
        this.shardProcessedTicks = new AtomicLong[shards];
        this.shardScheduled = new AtomicBoolean[shards];

        for (int i = 0; i < shards; i++) {
            final int shardIndex = i;
            final String name = i < workerCount ? "WiredTickShard-" + i : "WiredTickHeavy-" + (i - workerCount);
            this.shardExecutors[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                t.setPriority(threadPriority);
                return t;
            });
            this.shardRequestedTicks[i] = new AtomicLong(0L);
            this.shardProcessedTicks[i] = new AtomicLong(0L);
            this.shardScheduled[i] = new AtomicBoolean(false);
        }

        this.rehomeRooms();
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
                TimeUnit.MILLISECONDS);

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

        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            shard.clear();
        }
        roomShards.clear();
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
        int shardIndex = roomShards.computeIfAbsent(roomId, id -> desiredShard(id, System.currentTimeMillis()));
        Set<WiredTickable> tickables =
                shardRoomTickables[shardIndex].computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());

        if (tickables.add(tickable)) {
            tickable.onRegistered(room, System.currentTimeMillis());
        }
    }

    public void unregister(Room room, WiredTickable tickable) {
        if (room == null || tickable == null) {
            return;
        }

        int roomId = room.getId();
        // Every shard: a room being moved may briefly have tickables on two.
        boolean removed = false;
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            Set<WiredTickable> tickables = shard.get(roomId);
            if (tickables == null) {
                continue;
            }
            removed |= tickables.remove(tickable);
            if (tickables.isEmpty()) {
                shard.remove(roomId, tickables);
            }
        }
        if (removed) {
            tickable.onUnregistered(room);
        }
        forgetShardIfEmpty(roomId);
    }

    public void unregister(int roomId, int tickableId) {
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            Set<WiredTickable> tickables = shard.get(roomId);
            if (tickables == null) {
                continue;
            }

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
                shard.remove(roomId, tickables);
            }
        }
        forgetShardIfEmpty(roomId);
    }

    public void unregisterRoom(Room room) {
        if (room == null) {
            return;
        }

        int roomId = room.getId();
        Set<WiredTickable> tickables = ConcurrentHashMap.newKeySet();
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            Set<WiredTickable> removed = shard.remove(roomId);
            if (removed != null) {
                tickables.addAll(removed);
            }
        }
        this.roomShards.remove(roomId);
        this.pendingRoomTasks.remove(roomId);

        if (!tickables.isEmpty()) {
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
                            t);
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
        Set<WiredTickable> tickables = shardRoomTickables[currentShard(roomId)].get(roomId);

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
                            e);
                }
            }
        }
    }

    public int getTickableCount(int roomId) {
        int count = 0;
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            Set<WiredTickable> tickables = shard.get(roomId);
            count += tickables != null ? tickables.size() : 0;
        }
        return count;
    }

    public int getTotalTickableCount() {
        int count = 0;
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            count += shard.values().stream().mapToInt(Set::size).sum();
        }
        return count;
    }

    public int getActiveRoomCount() {
        int count = 0;
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            count += shard.size();
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

        for (int shardIndex = 0; shardIndex < shardRequestedTicks.length; shardIndex++) {
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

    /** Whether the current thread is the wired worker of this room. */
    public boolean isOnRoomWorker(int roomId) {
        Integer shard = CURRENT_SHARD.get();
        return shard != null && this.running.get() && this.shardExecutors != null && shard == currentShard(roomId);
    }

    /**
     * Runs a task on the room's own wired worker, so its cost only delays the rooms on that worker.
     * Answers false when the service is not running (the caller then runs it itself). A room with
     * {@link #MAX_PENDING_ROOM_TASKS} tasks waiting drops the task and answers true.
     */
    public boolean executeForRoom(int roomId, Runnable task) {
        ExecutorService[] executors = this.shardExecutors;
        if (!this.running.get() || executors == null || task == null) {
            return false;
        }

        java.util.concurrent.atomic.AtomicInteger pending =
                this.pendingRoomTasks.computeIfAbsent(roomId, key -> new java.util.concurrent.atomic.AtomicInteger());
        if (pending.incrementAndGet() > MAX_PENDING_ROOM_TASKS) {
            pending.decrementAndGet();
            if (shouldWarnSlow(roomId)) {
                LOGGER.warn("Room {} has {} wired tasks waiting; new ones are dropped", roomId, MAX_PENDING_ROOM_TASKS);
            }
            return true;
        }

        int shardIndex = currentShard(roomId);
        try {
            executors[shardIndex].execute(() -> {
                Integer previous = CURRENT_SHARD.get();
                CURRENT_SHARD.set(shardIndex);
                long started = System.currentTimeMillis();
                try {
                    task.run();
                } catch (Throwable t) {
                    LOGGER.error("Error in wired task for room {}", roomId, t);
                } finally {
                    pending.decrementAndGet();
                    if (previous == null) {
                        CURRENT_SHARD.remove();
                    } else {
                        CURRENT_SHARD.set(previous);
                    }
                    long took = System.currentTimeMillis() - started;
                    if (took > SLOW_ROOM_THRESHOLD_MS && shouldWarnSlow(roomId)) {
                        LOGGER.warn("Slow wired task: shard={}, room={}, took={}ms", shardIndex, roomId, took);
                    }
                }
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException rejected) {
            pending.decrementAndGet();
            return false;
        }
    }

    private void runShardLoop(int shardIndex) {
        CURRENT_SHARD.set(shardIndex);
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
            CURRENT_SHARD.remove();
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

            // Moved by its own worker between two ticks, so a room never ticks on two threads at once.
            int desired = desiredShard(roomId, shardStart);
            if (desired != shardIndex) {
                moveRoom(roomId, shardIndex, desired);
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
                    if (tickableDuration > SLOW_TICKABLE_THRESHOLD_MS && shouldWarnSlow(roomId)) {
                        LOGGER.warn(
                                "Slow wired tickable: shard={}, room={}, tick={}, tickableId={}, class={}, took={}ms",
                                shardIndex,
                                roomId,
                                currentTick,
                                tickable.getId(),
                                tickable.getClass().getName(),
                                tickableDuration);
                    }
                } catch (Throwable t) {
                    long tickableDuration = System.currentTimeMillis() - tickableStart;
                    LOGGER.error(
                            "Error in wired tick for tickable {} in room {} after {}ms",
                            tickable.getId(),
                            roomId,
                            tickableDuration,
                            t);
                }
            }

            long roomDuration = System.currentTimeMillis() - roomStart;
            if (roomDuration > SLOW_ROOM_THRESHOLD_MS && shouldWarnSlow(roomId)) {
                LOGGER.warn(
                        "Slow wired room tick: shard={}, room={}, tick={}, tickables={}, took={}ms",
                        shardIndex,
                        roomId,
                        currentTick,
                        tickables.size(),
                        roomDuration);
            }
        }

        long shardDuration = System.currentTimeMillis() - shardStart;
        if (shardDuration > SLOW_SHARD_THRESHOLD_MS && shouldWarnSlow(-1L - shardIndex)) {
            LOGGER.warn(
                    "Slow wired shard tick: shard={}, tick={}, rooms={}, tickables={}, took={}ms",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration);
        }

        if (debugEnabled && processedTickables > 0) {
            LOGGER.debug(
                    "Wired shard tick completed: shard={}, tick={}, rooms={}, tickables={}, took={}ms",
                    shardIndex,
                    currentTick,
                    processedRooms,
                    processedTickables,
                    shardDuration);
        }
    }

    private int totalShards() {
        return workerCount + heavyWorkerCount;
    }

    /** The shard a room belongs on now: a heavy shard while its wired is heavy, else its usual one. */
    private int desiredShard(int roomId, long now) {
        if (heavyWorkerCount > 0 && HeavyWiredRooms.isHeavy(roomId, now)) {
            return workerCount + Math.floorMod(roomId, heavyWorkerCount);
        }
        return Math.floorMod(roomId, workerCount);
    }

    /** The shard a room's work goes to: where its tickables are, else where it belongs. */
    int currentShard(int roomId) {
        Integer shard = roomShards.get(roomId);
        return shard != null && shard < totalShards() ? shard : desiredShard(roomId, System.currentTimeMillis());
    }

    private void moveRoom(int roomId, int from, int to) {
        Set<WiredTickable> moved = shardRoomTickables[from].remove(roomId);
        if (moved == null) {
            return;
        }
        shardRoomTickables[to].merge(roomId, moved, (existing, added) -> {
            existing.addAll(added);
            return existing;
        });
        roomShards.put(roomId, to);
        LOGGER.info(
                "Room {} wired {} the heavy workers ({} -> {})",
                roomId,
                to >= workerCount ? "moved to" : "moved back from",
                from,
                to);
    }

    /** Rooms registered before start may sit on shards of another worker count. */
    private void rehomeRooms() {
        long now = System.currentTimeMillis();
        for (int shard = 0; shard < shardRoomTickables.length; shard++) {
            for (Integer roomId : shardRoomTickables[shard].keySet()) {
                int desired = desiredShard(roomId, now);
                if (desired != shard) {
                    Set<WiredTickable> moved = shardRoomTickables[shard].remove(roomId);
                    if (moved != null) {
                        shardRoomTickables[desired].merge(roomId, moved, (existing, added) -> {
                            existing.addAll(added);
                            return existing;
                        });
                    }
                }
                roomShards.put(roomId, desired);
            }
        }
    }

    private void forgetShardIfEmpty(int roomId) {
        for (ConcurrentHashMap<Integer, Set<WiredTickable>> shard : shardRoomTickables) {
            if (shard.containsKey(roomId)) {
                return;
            }
        }
        roomShards.remove(roomId);
    }

    private boolean shouldWarnSlow(long key) {
        long now = System.currentTimeMillis();
        Long last = this.lastSlowWarningMs.get(key);
        if (last != null && now - last < SLOW_WARNING_INTERVAL_MS) {
            return false;
        }
        if (this.lastSlowWarningMs.size() >= MAX_SLOW_WARNING_KEYS) {
            this.lastSlowWarningMs.clear();
        }
        this.lastSlowWarningMs.put(key, now);
        return true;
    }
}
