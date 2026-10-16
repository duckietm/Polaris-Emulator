package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.items.interactions.wired.chest.WiredTradingManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Room-owned WIRED runtime state. */
public final class RoomWiredRuntime {
    private final AtomicLong cacheGeneration = new AtomicLong();
    private final WiredGravityService gravity;
    private final WiredOpacityService opacity;
    private final WiredTradingManager trading;
    private final WiredProjectileFlights projectiles = new WiredProjectileFlights();
    private final WiredBoxFlashes boxFlashes = new WiredBoxFlashes();

    RoomWiredRuntime(Room room) {
        this.gravity = new WiredGravityService(room);
        this.opacity = new WiredOpacityService(room);
        this.trading = new WiredTradingManager(room);
    }

    /** The wired boxes that flipped since the last room cycle, sent together by the cycle. */
    public WiredBoxFlashes getBoxFlashes() {
        return this.boxFlashes;
    }

    /** The room's open wired contract negotiations, one per player. */
    public WiredTradingManager getTradingManager() {
        return this.trading;
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

    /**
     * Stacks only depend on where the wired boxes are, so only a moved wired box clears the stack
     * cache; any other furni change (a toggle, a roller, a moved chair) keeps it.
     */
    void onFurnitureTopologyChanged(HabboItem item, boolean moved) {
        if (moved && item instanceof InteractionWired) {
            advanceCacheGeneration();
        }
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

    /** Room-wide opacity for one item, ignoring per-user overlays. Used by the {@code @opacity} variable. */
    public int globalOpacity(HabboItem item) {
        return item == null ? 100 : this.opacity.globalOpacity(item.getId());
    }

    /**
     * Sets the room-wide opacity for one item and pushes it to every client that announced opacity
     * support. Click-through is preserved, so a variable write only changes how transparent the item
     * is.
     */
    public boolean setGlobalOpacity(Room room, HabboItem item, int opacity) {
        if (room == null || item == null) {
            return false;
        }

        boolean clickThrough = this.opacity.globalClickThrough(item.getId());
        List<WiredOpacityState> applied = this.opacity.applyGlobal(List.of(item), opacity, clickThrough);
        if (applied.isEmpty()) {
            return false;
        }

        WiredOpacityBroadcaster.broadcast(room, applied, 0, 0);
        return true;
    }

    /** The flights projectile add-ons launched here, read by the {@code @projectile.*} variables. */
    public WiredProjectileFlights getProjectileFlights() {
        return this.projectiles;
    }

    void forgetProjectile(HabboItem item) {
        if (item != null) {
            this.projectiles.forget(item.getId());
        }
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
        this.projectiles.clear();
        // Every open negotiation is holding somebody's furniture out of their inventory. Letting the
        // room go without handing it back would lose it.
        this.trading.dispose();
    }
}
