package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal owner of WIRED stack lookup and source-trigger cache state.
 *
 * <p>The public {@link WiredStackIndex} remains unchanged and is still exposed by the engine for
 * plugin compatibility. Entries are scoped to both room lifecycle and wired mutation generations;
 * publication retries if invalidation overlaps a build.
 */
final class WiredStackRepository {

    private final WiredStackIndex index;
    private final ConcurrentHashMap<SourceCacheKey, CachedStacks> sourceStacksByTriggerKey;
    private final AtomicLong publicationEpoch;

    WiredStackRepository(WiredStackIndex index) {
        this.index = Objects.requireNonNull(index, "index");
        this.sourceStacksByTriggerKey = new ConcurrentHashMap<>();
        this.publicationEpoch = new AtomicLong();
    }

    List<WiredStack> getStacks(Room room, WiredEvent.Type eventType) {
        return this.index.getStacks(room, eventType);
    }

    WiredStackIndex eventIndex() {
        return this.index;
    }

    /** Multiple stacks can legally share one source trigger item. */
    List<WiredStack> getStacksForSourceItem(Room room, WiredEvent.Type eventType, int sourceItemId) {
        while (true) {
            SourceCacheKey cacheKey = SourceCacheKey.capture(room, eventType, sourceItemId);
            long epoch = this.publicationEpoch.get();
            this.removeStaleRoomEntries(cacheKey);

            CachedStacks cached = this.sourceStacksByTriggerKey.get(cacheKey);
            if (cached != null) {
                if (this.canReturn(room, cacheKey, cached, epoch)) {
                    return cached.stacks();
                }
                continue;
            }

            List<WiredStack> allStacks = this.index.getStacks(room, eventType);
            List<WiredStack> matching = new ArrayList<>();
            for (WiredStack stack : allStacks) {
                if (stack != null
                        && stack.triggerItem() != null
                        && stack.triggerItem().getId() == sourceItemId) {
                    matching.add(stack);
                }
            }

            List<WiredStack> result =
                    matching.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(matching);
            if (!cacheKey.matches(room) || epoch != this.publicationEpoch.get()) {
                continue;
            }

            CachedStacks candidate = new CachedStacks(result);
            CachedStacks previous = this.sourceStacksByTriggerKey.putIfAbsent(cacheKey, candidate);
            CachedStacks published = previous != null ? previous : candidate;
            if (this.canReturn(room, cacheKey, published, epoch)) {
                return published.stacks();
            }
            if (previous == null) {
                this.sourceStacksByTriggerKey.remove(cacheKey, candidate);
            }
        }
    }

    void clearRoomSourceStackCache(int roomId) {
        this.publicationEpoch.incrementAndGet();
        this.sourceStacksByTriggerKey.keySet().removeIf(key -> key.roomId() == roomId);
    }

    void clearAllSourceStackCache() {
        this.publicationEpoch.incrementAndGet();
        this.sourceStacksByTriggerKey.clear();
    }

    int sourceStackCacheSize() {
        return this.sourceStacksByTriggerKey.size();
    }

    private boolean canReturn(Room room, SourceCacheKey key, CachedStacks cached, long epoch) {
        return key.matches(room)
                && epoch == this.publicationEpoch.get()
                && this.sourceStacksByTriggerKey.get(key) == cached;
    }

    private void removeStaleRoomEntries(SourceCacheKey currentKey) {
        this.sourceStacksByTriggerKey
                .keySet()
                .removeIf(key -> key.roomId() == currentKey.roomId()
                        && (key.lifecycleGeneration() != currentKey.lifecycleGeneration()
                                || key.wiredGeneration() != currentKey.wiredGeneration()));
    }

    private record CachedStacks(List<WiredStack> stacks) {}

    private record SourceCacheKey(
            int roomId, long lifecycleGeneration, long wiredGeneration, WiredEvent.Type eventType, int sourceItemId) {

        static SourceCacheKey capture(Room room, WiredEvent.Type eventType, int sourceItemId) {
            return new SourceCacheKey(
                    room.getId(),
                    room.getLifecycleGeneration(),
                    room.getWiredCacheGeneration(),
                    eventType,
                    sourceItemId);
        }

        boolean matches(Room room) {
            return room != null
                    && room.getId() == this.roomId
                    && room.getLifecycleGeneration() == this.lifecycleGeneration
                    && room.getWiredCacheGeneration() == this.wiredGeneration;
        }
    }
}
