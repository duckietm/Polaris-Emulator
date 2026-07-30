package com.eu.habbo.habbohotel.games.snowwar.mapping;

import java.util.List;

/**
 * A single arena tile with collision data (README 5.5).
 */
public class SnowWarTile {

    private final int x;
    private final int y;
    private final boolean blocked;
    private final List<SnowWarItem> items;
    private final SnowWarItem highestItem;

    public SnowWarTile(int x, int y, boolean blocked, List<SnowWarItem> items) {
        this.x = x;
        this.y = y;
        this.blocked = blocked;
        this.items = items;

        SnowWarItem highest = null;
        for (SnowWarItem item : items) {
            if (highest == null || item.getWalkableHeight() > highest.getWalkableHeight()) {
                highest = item;
            }
        }
        this.highestItem = highest;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public boolean isBlocked() {
        return this.blocked;
    }

    public List<SnowWarItem> getItems() {
        return this.items;
    }

    public SnowWarItem getHighestItem() {
        return this.highestItem;
    }

    /**
     * Pathfinder collision: can a player walk on this tile? (README 5.5)
     */
    public boolean isWalkable() {
        if (this.blocked) {
            return false;
        }

        // Only solid furni (walkableHeight > 0) block movement; flat props such
        // as rugs/ice (walkableHeight 0) stay walkable. This mirrors the client
        // simulation, which blocks a tile only for walkableHeight > 0 items.
        return this.highestItem == null || this.highestItem.getWalkableHeight() <= 0;
    }

    /** AIR adds solid furni heights; trees collide through TreeGameObject. */
    public int getSnowballCollisionHeight() {
        int height = 0;
        for (SnowWarItem item : this.items) {
            if (!SnowWarItemProperties.isTreeName(item.getName()) && item.getCollisionHeight() > 0) {
                height += item.getCollisionHeight();
            }
        }
        return height;
    }
}
