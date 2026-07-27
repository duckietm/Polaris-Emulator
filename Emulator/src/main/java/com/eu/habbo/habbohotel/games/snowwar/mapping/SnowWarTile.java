package com.eu.habbo.habbohotel.games.snowwar.mapping;

import java.util.List;

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

    public boolean isWalkable() {
        if (this.blocked) {
            return false;
        }

        return this.highestItem == null || this.highestItem.getWalkableHeight() <= 0;
    }

    public boolean isHeightBlocking(int trajectory) {
        if (this.highestItem == null) {
            return false;
        }

        if (trajectory == 2) {
            return false;
        }

        return this.highestItem.blocksSnowball();
    }
}
