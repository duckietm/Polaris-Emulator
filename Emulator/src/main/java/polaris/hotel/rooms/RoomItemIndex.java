package polaris.hotel.rooms;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionPostIt;
import polaris.hotel.users.ItemFootprintListener;
import polaris.hotel.users.UserItem;

final class RoomItemIndex {

    private final Room room;
    private final Int2ObjectMap<UserItem> items = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2ObjectMap<String> ownerNames = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>(0));
    private final Int2IntMap ownerCounts = Int2IntMaps.synchronize(new Int2IntOpenHashMap(0));
    private final Int2LongMap itemIncarnations = Int2LongMaps.synchronize(new Int2LongOpenHashMap(0));
    private final AtomicLong incarnationSequence = new AtomicLong();
    private final ConcurrentHashMap<RoomTile, Set<UserItem>> tileCache = new ConcurrentHashMap<>();
    private final RoomBuildersClubIdBand buildersClubIds = new RoomBuildersClubIdBand();

    private static final long[] NO_TILES = new long[0];
    /** Floor furni by the tiles they cover, so a tile lookup no longer scans every furni. Guarded by items. */
    private final Map<Long, List<UserItem>> itemsByTile = new HashMap<>();
    /** The tiles each furni was filed under, by item id (none for wall furni). Guarded by items. */
    private final Int2ObjectMap<long[]> indexedFootprints = new Int2ObjectOpenHashMap<>();
    /** Furni that moved, joined or left since the last lookup; refiled before the next one. */
    private final Set<UserItem> movedItems = ConcurrentHashMap.newKeySet();

    private final ItemFootprintListener footprintListener = this.movedItems::add;
    private boolean tileIndexBuilt = false;

    RoomItemIndex(Room room) {
        this.room = room;
    }

    Int2ObjectMap<UserItem> items() {
        return this.items;
    }

    Int2ObjectMap<String> ownerNames() {
        return this.ownerNames;
    }

    Int2IntMap ownerCounts() {
        return this.ownerCounts;
    }

    ConcurrentHashMap<RoomTile, Set<UserItem>> tileCache() {
        return this.tileCache;
    }

    RoomBuildersClubIdBand buildersClubIds() {
        return this.buildersClubIds;
    }

    UserItem get(int id) {
        if (this.room.getRoomSpecialTypes() == null) {
            return null;
        }

        if (BuildersClubItemIds.isBandId(id)) {
            // A Builders Club wire id: only the furni holding it here, never a database id that happens to match.
            id = this.buildersClubIds.itemOf(id);
            if (id == 0) {
                return null;
            }
        }

        UserItem item;
        synchronized (this.items) {
            item = this.items.get(id);
        }

        return item != null ? item : this.room.getRoomSpecialTypes().getSpecialItem(id);
    }

    long registerIncarnation(UserItem item) {
        if (item == null) {
            return 0L;
        }
        long incarnation = this.incarnationSequence.incrementAndGet();
        this.itemIncarnations.put(item.getId(), incarnation);
        return incarnation;
    }

    void unregisterIncarnation(UserItem item) {
        if (item != null) {
            this.itemIncarnations.remove(item.getId());
        }
    }

    long itemIncarnation(int itemId) {
        return this.itemIncarnations.getOrDefault(itemId, 0L);
    }

    int size() {
        return this.items.size();
    }

    Set<UserItem> floorItems() {
        return this.itemsOfType(FurnitureType.FLOOR);
    }

    Set<UserItem> wallItems() {
        return this.itemsOfType(FurnitureType.WALL);
    }

    Set<UserItem> postItNotes() {
        Set<UserItem> result = new HashSet<>();
        synchronized (this.items) {
            for (UserItem item : this.items.values()) {
                if (item.getBaseItem().getInteractionType().getType() == InteractionPostIt.class) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    Set<UserItem> itemsAt(RoomTile tile, boolean returnOnFirst) {
        if (tile == null) {
            return new HashSet<>(0);
        }

        boolean loaded = this.room.isLoaded();
        if (loaded && this.movedItems.isEmpty()) {
            Set<UserItem> cachedItems = this.tileCache.get(tile);
            if (cachedItems != null) {
                return cachedItems;
            }
        }

        synchronized (this.items) {
            this.syncTileIndex();

            if (loaded) {
                Set<UserItem> cachedItems = this.tileCache.get(tile);
                if (cachedItems != null) {
                    return cachedItems;
                }
            }

            List<UserItem> onTile = this.itemsByTile.get(tileKey(tile.x, tile.y));
            Set<UserItem> result;
            if (onTile == null || onTile.isEmpty()) {
                result = new HashSet<>(0);
            } else if (returnOnFirst) {
                result = new HashSet<>(1);
                result.add(onTile.get(0));
                return result;
            } else {
                result = new HashSet<>(onTile);
            }

            if (loaded) {
                this.tileCache.put(tile, result);
            }
            return result;
        }
    }

    /** Called when a furni joins the room: the tile index files it on the next lookup. */
    void trackItem(UserItem item) {
        if (item != null) {
            item.setFootprintListener(this.footprintListener);
            this.movedItems.add(item);
        }
    }

    /** Called when a furni leaves the room: the tile index drops it on the next lookup. */
    void untrackItem(UserItem item) {
        if (item != null) {
            item.clearFootprintListener(this.footprintListener);
            this.movedItems.add(item);
        }
    }

    /** Brings the tile index up to date: only the furni that moved are refiled. Holds the items lock. */
    private void syncTileIndex() {
        if (!this.tileIndexBuilt) {
            this.rebuildTileIndex();
            return;
        }

        if (!this.movedItems.isEmpty()) {
            Iterator<UserItem> moved = this.movedItems.iterator();
            while (moved.hasNext()) {
                UserItem item = moved.next();
                moved.remove();
                this.refile(item);
            }
        }

        // Furni put straight into the item map (plugins, tests) were never tracked: start over.
        if (this.indexedFootprints.size() != this.items.size()) {
            this.rebuildTileIndex();
        }
    }

    private void rebuildTileIndex() {
        this.itemsByTile.clear();
        this.indexedFootprints.clear();
        this.movedItems.clear();
        this.tileCache.clear();

        for (UserItem item : this.items.values()) {
            if (item == null) {
                continue;
            }
            item.setFootprintListener(this.footprintListener);
            this.file(item);
        }
        this.tileIndexBuilt = true;
    }

    private void refile(UserItem item) {
        long[] previous = this.indexedFootprints.remove(item.getId());
        if (previous != null) {
            int itemId = item.getId();
            for (long key : previous) {
                List<UserItem> onTile = this.itemsByTile.get(key);
                if (onTile != null) {
                    onTile.removeIf(indexed -> indexed.getId() == itemId);
                    if (onTile.isEmpty()) {
                        this.itemsByTile.remove(key);
                    }
                }
                this.evictCachedTile(key);
            }
        }

        if (this.items.get(item.getId()) == item) {
            for (long key : this.file(item)) {
                this.evictCachedTile(key);
            }
        }
    }

    private long[] file(UserItem item) {
        long[] footprint = footprint(item);
        this.indexedFootprints.put(item.getId(), footprint);
        for (long key : footprint) {
            this.itemsByTile.computeIfAbsent(key, ignored -> new ArrayList<>(2)).add(item);
        }
        return footprint;
    }

    private void evictCachedTile(long key) {
        short x = (short) (key >> 32);
        short y = (short) key;
        RoomLayout layout = this.room.getLayout();
        RoomTile tile = (layout != null) ? layout.getTile(x, y) : null;
        // Tiles are keyed by position, so any tile at the same spot finds the cached entry.
        this.tileCache.remove((tile != null) ? tile : new RoomTile(x, y, (short) 0, RoomTileState.INVALID, false));
    }

    /** The tiles a floor furni covers, the same rule the old whole-room scan used. */
    static long[] footprint(UserItem item) {
        if (item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) {
            return NO_TILES;
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

        long[] keys = new long[width * length];
        int index = 0;
        for (int dx = 0; dx < width; dx++) {
            for (int dy = 0; dy < length; dy++) {
                keys[index++] = tileKey(item.getX() + dx, item.getY() + dy);
            }
        }
        return keys;
    }

    static long tileKey(int x, int y) {
        return ((long) (short) x << 32) | ((short) y & 0xFFFFL);
    }

    private Set<UserItem> itemsOfType(FurnitureType type) {
        Set<UserItem> result = new HashSet<>();
        synchronized (this.items) {
            for (UserItem item : this.items.values()) {
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
        this.buildersClubIds.clear();
        synchronized (this.ownerCounts) {
            this.ownerCounts.clear();
        }
        synchronized (this.ownerNames) {
            this.ownerNames.clear();
        }
        this.itemIncarnations.clear();
        synchronized (this.items) {
            this.itemsByTile.clear();
            this.indexedFootprints.clear();
            this.movedItems.clear();
            this.tileIndexBuilt = false;
        }
        this.tileCache.clear();
    }
}
