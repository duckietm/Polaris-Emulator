package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionPostIt;
import com.eu.habbo.habbohotel.users.HabboItem;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class RoomItemIndex {

    private final Room room;
    private final Int2ObjectMap<HabboItem> items =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<String> ownerNames =
            Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2IntMap ownerCounts =
            Int2IntMaps.synchronize(new Int2IntOpenHashMap(0));
    private final ConcurrentHashMap<RoomTile, Set<HabboItem>> tileCache =
            new ConcurrentHashMap<>();

    RoomItemIndex(Room room) {
        this.room = room;
    }

    Int2ObjectMap<HabboItem> items() {
        return this.items;
    }

    Int2ObjectMap<String> ownerNames() {
        return this.ownerNames;
    }

    Int2IntMap ownerCounts() {
        return this.ownerCounts;
    }

    ConcurrentHashMap<RoomTile, Set<HabboItem>> tileCache() {
        return this.tileCache;
    }

    HabboItem get(int id) {
        if (this.room.getRoomSpecialTypes() == null) {
            return null;
        }

        HabboItem item;
        synchronized (this.items) {
            item = this.items.get(id);
        }

        return item != null
                ? item
                : this.room.getRoomSpecialTypes().getSpecialItem(id);
    }

    int size() {
        return this.items.size();
    }

    Set<HabboItem> floorItems() {
        return this.itemsOfType(FurnitureType.FLOOR);
    }

    Set<HabboItem> wallItems() {
        return this.itemsOfType(FurnitureType.WALL);
    }

    Set<HabboItem> postItNotes() {
        Set<HabboItem> result = new HashSet<>();
        synchronized (this.items) {
            for (HabboItem item : this.items.values()) {
                if (item.getBaseItem().getInteractionType().getType()
                        == InteractionPostIt.class) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    Set<HabboItem> itemsAt(RoomTile tile, boolean returnOnFirst) {
        Set<HabboItem> result = new HashSet<>(0);
        if (tile == null) {
            return result;
        }

        if (this.room.isLoaded()) {
            Set<HabboItem> cachedItems = this.tileCache.get(tile);
            if (cachedItems != null) {
                return cachedItems;
            }
        }

        synchronized (this.items) {
            for (HabboItem item : this.items.values()) {
                if (item == null
                        || item.getBaseItem().getType()
                        != FurnitureType.FLOOR) {
                    continue;
                }

                int width;
                int length;
                if (item.getRotation() != 2 && item.getRotation() != 6) {
                    width = Math.max(item.getBaseItem().getWidth(), 1);
                    length = Math.max(item.getBaseItem().getLength(), 1);
                } else {
                    width = Math.max(item.getBaseItem().getLength(), 1);
                    length = Math.max(item.getBaseItem().getWidth(), 1);
                }

                if (tile.x < item.getX()
                        || tile.x > item.getX() + width - 1
                        || tile.y < item.getY()
                        || tile.y > item.getY() + length - 1) {
                    continue;
                }

                result.add(item);
                if (returnOnFirst) {
                    return result;
                }
            }
        }

        if (this.room.isLoaded()) {
            this.tileCache.put(tile, result);
        }
        return result;
    }

    private Set<HabboItem> itemsOfType(FurnitureType type) {
        Set<HabboItem> result = new HashSet<>();
        synchronized (this.items) {
            for (HabboItem item : this.items.values()) {
                if (item.getBaseItem().getType() == type) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    void clear() {
        synchronized (this.items) {
            this.items.clear();
        }
        synchronized (this.ownerCounts) {
            this.ownerCounts.clear();
        }
        synchronized (this.ownerNames) {
            this.ownerNames.clear();
        }
        this.tileCache.clear();
    }
}
