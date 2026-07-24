package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.concurrent.atomic.AtomicLong;

/** Room-owned WIRED runtime state. */
public final class RoomWiredRuntime {
    private final AtomicLong cacheGeneration = new AtomicLong();
    private final WiredGravityService gravity;

    RoomWiredRuntime(Room room) {
        this.gravity = new WiredGravityService(room);
    }

    long cacheGeneration() {
        return this.cacheGeneration.get();
    }

    long advanceCacheGeneration() {
        return this.cacheGeneration.incrementAndGet();
    }

    public boolean setGravityEnabled(HabboItem item, boolean enabled) {
        return this.gravity.setEnabled(item, enabled);
    }

    public boolean isGravityEnabled(HabboItem item) {
        return this.gravity.isEnabled(item);
    }

    public void markFurnitureMoving(HabboItem item, int durationMs) {
        this.gravity.markMoving(item, durationMs);
    }

    void onFurnitureTopologyChanged() {
        advanceCacheGeneration();
        this.gravity.onTopologyChanged();
    }

    void forgetGravity(HabboItem item) {
        this.gravity.forget(item);
    }

    void dispose() {
        this.gravity.dispose();
    }
}
