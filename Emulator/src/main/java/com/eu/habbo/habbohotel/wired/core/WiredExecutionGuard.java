package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.LongSupplier;

/** Internal owner of room-level WIRED recursion, rate, ban and diagnostic state. */
final class WiredExecutionGuard {

    private static final LimitSource LIVE_LIMITS = new LimitSource() {
        @Override
        public int maxRecursionDepth() {
            return WiredEngine.MAX_RECURSION_DEPTH;
        }

        @Override
        public int maxEventsPerWindow() {
            return WiredEngine.MAX_EVENTS_PER_WINDOW;
        }

        @Override
        public long rateLimitWindowMs() {
            return WiredEngine.RATE_LIMIT_WINDOW_MS;
        }

        @Override
        public long banDurationMs() {
            return WiredEngine.WIRED_BAN_DURATION_MS;
        }

        @Override
        public int monitorUsageWindowMs() {
            return WiredEngine.MONITOR_USAGE_WINDOW_MS;
        }

        @Override
        public int monitorUsageLimit() {
            return WiredEngine.MONITOR_USAGE_LIMIT;
        }

        @Override
        public int monitorDelayedEventsLimit() {
            return WiredEngine.MONITOR_DELAYED_EVENTS_LIMIT;
        }

        @Override
        public int monitorOverloadAverageMs() {
            return WiredEngine.MONITOR_OVERLOAD_AVERAGE_MS;
        }

        @Override
        public int monitorOverloadPeakMs() {
            return WiredEngine.MONITOR_OVERLOAD_PEAK_MS;
        }

        @Override
        public int monitorHeavyUsagePercent() {
            return WiredEngine.MONITOR_HEAVY_USAGE_PERCENT;
        }

        @Override
        public int monitorHeavyConsecutiveWindows() {
            return WiredEngine.MONITOR_HEAVY_CONSECUTIVE_WINDOWS;
        }

        @Override
        public int monitorOverloadConsecutiveWindows() {
            return WiredEngine.MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS;
        }

        @Override
        public int monitorHeavyDelayedPercent() {
            return WiredEngine.MONITOR_HEAVY_DELAYED_PERCENT;
        }
    };

    enum EntryKind {
        EVENT,
        SOURCE_ITEM
    }

    interface LimitSource {
        int maxRecursionDepth();

        int maxEventsPerWindow();

        long rateLimitWindowMs();

        long banDurationMs();

        int monitorUsageWindowMs();

        int monitorUsageLimit();

        int monitorDelayedEventsLimit();

        int monitorOverloadAverageMs();

        int monitorOverloadPeakMs();

        int monitorHeavyUsagePercent();

        int monitorHeavyConsecutiveWindows();

        int monitorOverloadConsecutiveWindows();

        int monitorHeavyDelayedPercent();
    }

    record Limits(
            int maxRecursionDepth,
            int maxEventsPerWindow,
            long rateLimitWindowMs,
            long banDurationMs,
            int monitorUsageWindowMs,
            int monitorUsageLimit,
            int monitorDelayedEventsLimit,
            int monitorOverloadAverageMs,
            int monitorOverloadPeakMs,
            int monitorHeavyUsagePercent,
            int monitorHeavyConsecutiveWindows,
            int monitorOverloadConsecutiveWindows,
            int monitorHeavyDelayedPercent)
            implements LimitSource {}

    @FunctionalInterface
    interface RateLimitSink {
        void onLimit(Room room, WiredEvent.Type eventType, int eventCount, LimitSource limits, boolean banned);
    }

    @FunctionalInterface
    interface RecursionLimitSink {
        void onLimit(Room room, WiredEvent.Type eventType, EntryKind kind, int currentDepth, int maximumDepth);
    }

    private final LimitSource fixedLimits;
    private final LongSupplier clock;
    private final RateLimitSink rateLimitSink;
    private final RecursionLimitSink recursionLimitSink;
    private final ThreadLocal<RecursionStack> currentChainRecursionDepth = ThreadLocal.withInitial(RecursionStack::new);
    private final ConcurrentHashMap<Integer, ActiveRecursionDepths> activeRoomRecursionDepth =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, EventRateTracker> eventRateLimiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Long> bannedRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, WiredRoomDiagnostics> roomDiagnostics = new ConcurrentHashMap<>();
    private volatile ActiveRoomCache recentActiveRoom;
    private volatile RateTrackerCache recentRateTracker;

    WiredExecutionGuard(
            LimitSource limits,
            LongSupplier clock,
            RateLimitSink rateLimitSink,
            RecursionLimitSink recursionLimitSink) {
        this.fixedLimits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rateLimitSink = Objects.requireNonNull(rateLimitSink, "rateLimitSink");
        this.recursionLimitSink = Objects.requireNonNull(recursionLimitSink, "recursionLimitSink");
    }

    WiredExecutionGuard(LongSupplier clock, RateLimitSink rateLimitSink, RecursionLimitSink recursionLimitSink) {
        this.fixedLimits = null;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rateLimitSink = Objects.requireNonNull(rateLimitSink, "rateLimitSink");
        this.recursionLimitSink = Objects.requireNonNull(recursionLimitSink, "recursionLimitSink");
    }

    boolean tryEnter(Room room, WiredEvent.Type eventType, EntryKind kind) {
        return tryEnter(room.getId(), room, eventType, kind, true);
    }

    boolean tryEnterDeferredPublication(int roomId, Room room, WiredEvent.Type eventType, EntryKind kind) {
        return tryEnter(roomId, room, eventType, kind, false);
    }

    private boolean tryEnter(int roomId, Room room, WiredEvent.Type eventType, EntryKind kind, boolean publishDepth) {
        long now = this.clock.getAsLong();
        if (isRoomBanned(roomId, now)) {
            return false;
        }

        if (isRateLimited(roomId, room, eventType, now)) {
            return false;
        }

        RecursionStack recursionStack = this.currentChainRecursionDepth.get();
        int currentDepth = recursionStack.depth(roomId) + 1;
        int maximumDepth = maxRecursionDepth();
        if (currentDepth <= maximumDepth) {
            recursionStack.push(roomId, currentDepth);
            if (publishDepth) {
                publishCurrentDepth(roomId, currentDepth);
            }
            return true;
        }

        if (kind == EntryKind.EVENT) {
            diagnostics(roomId)
                    .recordRecursionTimeout(
                            now,
                            String.format(
                                    "Recursion depth %d/%d while handling %s",
                                    currentDepth, maximumDepth, eventType.name()),
                            eventType.name(),
                            0);
        }
        this.recursionLimitSink.onLimit(room, eventType, kind, currentDepth, maximumDepth);
        return false;
    }

    void exit(int roomId) {
        exit(roomId, true);
    }

    void publishDeferredAdmission(int roomId) {
        int currentDepth = this.currentChainRecursionDepth.get().depth(roomId);
        if (currentDepth > 0) {
            publishCurrentDepth(roomId, currentDepth);
        }
    }

    void exitDeferredAdmission(int roomId, boolean published) {
        exit(roomId, published);
    }

    private void exit(int roomId, boolean published) {
        int currentDepth = this.currentChainRecursionDepth.get().pop(roomId);
        if (published) {
            removePublishedDepth(roomId, currentDepth);
        }
    }

    int recursionDepth(int roomId) {
        ActiveRecursionDepths activeDepths = this.activeRoomRecursionDepth.get(roomId);
        return activeDepths == null ? 0 : activeDepths.deepest();
    }

    int currentChainDepth(int roomId) {
        return this.currentChainRecursionDepth.get().depth(roomId);
    }

    WiredRoomDiagnostics diagnostics(int roomId) {
        return this.roomDiagnostics.computeIfAbsent(roomId, ignored -> newDiagnostics(currentLimits()));
    }

    WiredRoomDiagnostics.Snapshot snapshot(int roomId) {
        long now = this.clock.getAsLong();
        long killedUntil = this.bannedRooms.getOrDefault(roomId, 0L);
        return diagnostics(roomId).snapshot(recursionDepth(roomId), maxRecursionDepth(), killedUntil, now);
    }

    void clearRoomRecursionDepth(int roomId) {
        this.activeRoomRecursionDepth.remove(roomId);
        ActiveRoomCache cached = this.recentActiveRoom;
        if (cached != null && cached.roomId() == roomId) {
            this.recentActiveRoom = null;
        }
        this.currentChainRecursionDepth.get().remove(roomId);
    }

    void clearAllRecursionDepth() {
        this.activeRoomRecursionDepth.clear();
        this.recentActiveRoom = null;
        this.currentChainRecursionDepth.remove();
    }

    private void publishCurrentDepth(int roomId, int currentDepth) {
        activeDepths(roomId).enter(currentDepth);
    }

    private void removePublishedDepth(int roomId, int currentDepth) {
        ActiveRecursionDepths activeDepths = existingActiveDepths(roomId);
        if (activeDepths != null && currentDepth > 0) {
            activeDepths.exit(currentDepth);
        }
    }

    private ActiveRecursionDepths activeDepths(int roomId) {
        ActiveRoomCache cached = this.recentActiveRoom;
        if (cached != null && cached.roomId() == roomId) {
            return cached.activeDepths();
        }
        ActiveRecursionDepths activeDepths =
                this.activeRoomRecursionDepth.computeIfAbsent(roomId, ignored -> new ActiveRecursionDepths());
        this.recentActiveRoom = new ActiveRoomCache(roomId, activeDepths);
        return activeDepths;
    }

    private ActiveRecursionDepths existingActiveDepths(int roomId) {
        ActiveRoomCache cached = this.recentActiveRoom;
        return cached != null && cached.roomId() == roomId
                ? cached.activeDepths()
                : this.activeRoomRecursionDepth.get(roomId);
    }

    void clearRoomRateLimiters(int roomId) {
        String prefix = roomId + ":";
        this.eventRateLimiters.keySet().removeIf(key -> key.startsWith(prefix));
        RateTrackerCache cached = this.recentRateTracker;
        if (cached != null && cached.roomId() == roomId) {
            this.recentRateTracker = null;
        }
    }

    void clearAllRateLimiters() {
        this.eventRateLimiters.clear();
        this.recentRateTracker = null;
    }

    void clearRoomDiagnostics(int roomId) {
        this.roomDiagnostics.remove(roomId);
    }

    void clearAllDiagnostics() {
        this.roomDiagnostics.clear();
    }

    void clearRoomDiagnosticsLogs(int roomId) {
        WiredRoomDiagnostics diagnostics = this.roomDiagnostics.get(roomId);
        if (diagnostics != null) {
            diagnostics.clearLogs();
        }
    }

    void clearRoomBan(int roomId) {
        this.bannedRooms.remove(roomId);
    }

    private boolean isRoomBanned(int roomId, long now) {
        Long banExpiry = this.bannedRooms.get(roomId);
        if (banExpiry == null) {
            return false;
        }

        if (now >= banExpiry) {
            this.bannedRooms.remove(roomId, banExpiry);
            return false;
        }
        return true;
    }

    private boolean isRateLimited(int roomId, Room room, WiredEvent.Type eventType, long now) {
        long windowMs = rateLimitWindowMs();
        int maximumEvents = maxEventsPerWindow();
        RateTrackerCache cached = this.recentRateTracker;
        EventRateTracker tracker;
        if (cached != null && cached.roomId() == roomId && cached.eventType() == eventType) {
            tracker = cached.tracker();
            tracker.recordEvent(now, windowMs);
        } else {
            String key = roomId + ":" + eventType.name();
            tracker = this.eventRateLimiters.get(key);
            if (tracker == null) {
                EventRateTracker candidate = new EventRateTracker(now);
                tracker = this.eventRateLimiters.putIfAbsent(key, candidate);
                if (tracker == null) {
                    tracker = candidate;
                } else {
                    tracker.recordEvent(now, windowMs);
                }
            } else {
                tracker.recordEvent(now, windowMs);
            }
            this.recentRateTracker = new RateTrackerCache(roomId, eventType, tracker);
        }

        boolean limited = tracker.isRateLimited(maximumEvents);
        if (limited && tracker.shouldBan(maximumEvents)) {
            int eventCount = tracker.eventCount();
            diagnostics(roomId)
                    .recordKilled(
                            now,
                            String.format(
                                    "Rate limit exceeded for %s with %d event(s) in %dms",
                                    eventType.name(), eventCount, windowMs),
                            eventType.name(),
                            0);

            long banDurationMs = banDurationMs();
            boolean banned = banDurationMs > 0;
            if (banned) {
                this.bannedRooms.put(roomId, now + banDurationMs);
            }
            this.rateLimitSink.onLimit(room, eventType, eventCount, currentLimits(), banned);
        }
        return limited;
    }

    private LimitSource currentLimits() {
        return this.fixedLimits != null ? this.fixedLimits : LIVE_LIMITS;
    }

    private int maxRecursionDepth() {
        return this.fixedLimits != null ? this.fixedLimits.maxRecursionDepth() : WiredEngine.MAX_RECURSION_DEPTH;
    }

    private int maxEventsPerWindow() {
        return this.fixedLimits != null ? this.fixedLimits.maxEventsPerWindow() : WiredEngine.MAX_EVENTS_PER_WINDOW;
    }

    private long rateLimitWindowMs() {
        return this.fixedLimits != null ? this.fixedLimits.rateLimitWindowMs() : WiredEngine.RATE_LIMIT_WINDOW_MS;
    }

    private long banDurationMs() {
        return this.fixedLimits != null ? this.fixedLimits.banDurationMs() : WiredEngine.WIRED_BAN_DURATION_MS;
    }

    private static WiredRoomDiagnostics newDiagnostics(LimitSource limits) {
        return new WiredRoomDiagnostics(
                limits.monitorUsageWindowMs(),
                limits.monitorUsageLimit(),
                limits.monitorDelayedEventsLimit(),
                limits.monitorOverloadAverageMs(),
                limits.monitorOverloadPeakMs(),
                limits.monitorHeavyUsagePercent(),
                limits.monitorHeavyConsecutiveWindows(),
                limits.monitorOverloadConsecutiveWindows(),
                limits.monitorHeavyDelayedPercent(),
                200);
    }

    private static final class ActiveRecursionDepths {
        private static final int FAST_DEPTH_COUNT = 64;

        private final AtomicIntegerArray counts = new AtomicIntegerArray(FAST_DEPTH_COUNT);
        private final ConcurrentHashMap<Integer, AtomicInteger> overflow = new ConcurrentHashMap<>();

        private void enter(int depth) {
            if (depth < FAST_DEPTH_COUNT) {
                this.counts.incrementAndGet(depth);
                return;
            }
            this.overflow.computeIfAbsent(depth, ignored -> new AtomicInteger()).incrementAndGet();
        }

        private void exit(int depth) {
            if (depth < FAST_DEPTH_COUNT) {
                this.counts.decrementAndGet(depth);
                return;
            }
            this.overflow.computeIfPresent(depth, (ignored, count) -> count.decrementAndGet() <= 0 ? null : count);
        }

        private int deepest() {
            int deepest = 0;
            for (Map.Entry<Integer, AtomicInteger> entry : this.overflow.entrySet()) {
                if (entry.getValue().get() > 0) {
                    deepest = Math.max(deepest, entry.getKey());
                }
            }
            if (deepest >= FAST_DEPTH_COUNT) {
                return deepest;
            }
            for (int depth = FAST_DEPTH_COUNT - 1; depth > 0; depth--) {
                if (this.counts.get(depth) > 0) {
                    return depth;
                }
            }
            return 0;
        }
    }

    private record ActiveRoomCache(int roomId, ActiveRecursionDepths activeDepths) {}

    private record RateTrackerCache(int roomId, WiredEvent.Type eventType, EventRateTracker tracker) {}

    private static final class RecursionStack {
        private int[] roomIds = new int[16];
        private int[] depths = new int[16];
        private int size;

        private int depth(int roomId) {
            if (this.size > 0 && this.roomIds[this.size - 1] == roomId) {
                return this.depths[this.size - 1];
            }
            int depth = 0;
            for (int index = 0; index < this.size; index++) {
                if (this.roomIds[index] == roomId) {
                    depth++;
                }
            }
            return depth;
        }

        private void push(int roomId, int depth) {
            if (this.size == this.roomIds.length) {
                this.roomIds = Arrays.copyOf(this.roomIds, this.roomIds.length * 2);
                this.depths = Arrays.copyOf(this.depths, this.depths.length * 2);
            }
            this.roomIds[this.size] = roomId;
            this.depths[this.size++] = depth;
        }

        private int pop(int roomId) {
            if (this.size == 0) {
                return 0;
            }
            if (this.roomIds[this.size - 1] == roomId) {
                int index = --this.size;
                int depth = this.depths[index];
                this.roomIds[index] = 0;
                this.depths[index] = 0;
                return depth;
            }
            for (int index = this.size - 1; index >= 0; index--) {
                if (this.roomIds[index] != roomId) {
                    continue;
                }
                int depth = this.depths[index];
                int moved = this.size - index - 1;
                if (moved > 0) {
                    System.arraycopy(this.roomIds, index + 1, this.roomIds, index, moved);
                    System.arraycopy(this.depths, index + 1, this.depths, index, moved);
                }
                this.roomIds[--this.size] = 0;
                this.depths[this.size] = 0;
                return depth;
            }
            return 0;
        }

        private void remove(int roomId) {
            int target = 0;
            for (int index = 0; index < this.size; index++) {
                if (this.roomIds[index] != roomId) {
                    this.roomIds[target++] = this.roomIds[index];
                    this.depths[target - 1] = this.depths[index];
                }
            }
            Arrays.fill(this.roomIds, target, this.size, 0);
            Arrays.fill(this.depths, target, this.size, 0);
            this.size = target;
        }
    }

    private static final class EventRateTracker {
        private long windowStart;
        private int eventCount;
        private boolean warned;

        private EventRateTracker(long now) {
            this.windowStart = now;
            this.eventCount = 1;
        }

        private synchronized void recordEvent(long now, long windowMs) {
            if (now - this.windowStart > windowMs) {
                this.windowStart = now;
                this.eventCount = 1;
                this.warned = false;
            } else {
                this.eventCount++;
            }
        }

        private synchronized boolean isRateLimited(int maximumEvents) {
            return this.eventCount > maximumEvents;
        }

        private synchronized boolean shouldBan(int maximumEvents) {
            if (this.eventCount > maximumEvents && !this.warned) {
                this.warned = true;
                return true;
            }
            return false;
        }

        private synchronized int eventCount() {
            return this.eventCount;
        }
    }
}
