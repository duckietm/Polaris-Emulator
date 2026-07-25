package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Room-owned WIRED runtime state. */
public final class RoomWiredRuntime {
    private final AtomicLong cacheGeneration = new AtomicLong();
    private final WiredGravityService gravity;
    private final WiredOpacityService opacity;

    RoomWiredRuntime(Room room) {
        this.gravity = new WiredGravityService(room);
        this.opacity = new WiredOpacityService(room);
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

    public List<WiredOpacityState> applyGlobalOpacity(Collection<HabboItem> items, int opacity, boolean clickThrough) {
        return this.opacity.applyGlobal(items, opacity, clickThrough);
    }

    public List<WiredOpacityState> applyUserOpacity(
            int userId, Collection<HabboItem> items, int opacity, boolean clickThrough) {
        return this.opacity.applyUser(userId, items, opacity, clickThrough);
    }

    public List<WiredOpacityState> effectiveOpacity(int userId, Collection<Integer> itemIds) {
        return this.opacity.effective(userId, itemIds);
    }

    public List<WiredOpacityState> opacitySnapshot(int userId) {
        return this.opacity.snapshot(userId);
    }

    void forgetOpacity(HabboItem item) {
        this.opacity.forgetItem(item);
    }

    void forgetOpacityUser(int userId) {
        this.opacity.forgetUser(userId);
    }

    void dispose() {
        this.gravity.dispose();
        this.opacity.dispose();
    }
}
