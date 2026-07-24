package com.eu.habbo.habbohotel.rooms;

import java.util.concurrent.atomic.AtomicLong;

/** Room-owned WIRED runtime state. */
public final class RoomWiredRuntime {
    private final AtomicLong cacheGeneration = new AtomicLong();

    RoomWiredRuntime(Room room) {}

    long cacheGeneration() {
        return this.cacheGeneration.get();
    }

    long advanceCacheGeneration() {
        return this.cacheGeneration.incrementAndGet();
    }

    void onFurnitureTopologyChanged() {
        advanceCacheGeneration();
    }

    void dispose() {}
}
