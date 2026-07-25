package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWired;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages all items/furniture within a room.
 * Handles loading, adding, removing, querying, and picking up items.
 */
public class RoomItemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemManager.class);

    private final Room room;
    private final RoomItemIndex index;
    private final RoomItemMovementService movement;
    private final RoomItemOperations operations;
    private final RoomItemOwnershipService ownership;
    private final RoomItemPlacementService placement;
    private final RoomItemRegistry registry;

    // Tile cache for item lookups
    public final ConcurrentHashMap<RoomTile, Set<HabboItem>> tileCache;

    public RoomItemManager(Room room) {
        this.room = room;
        this.index = new RoomItemIndex(room);
        this.operations = new RoomItemOperations(room);
        this.registry = new RoomItemRegistry(room);
        this.ownership = new RoomItemOwnershipService(room, this.index, this.registry);
        this.placement = new RoomItemPlacementService(room, this.index, this);
        this.movement = new RoomItemMovementService(room, this, this.placement);
        this.tileCache = this.index.tileCache();
    }

    // ==================== LOADING ====================

    /**
     * Loads items from the database.
     */
    public void loadItems(Connection connection) {
        synchronized (this.index.items()) {
            this.index.items().clear();
        }

        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM items WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.addHabboItem(
                            Emulator.getGameEnvironment().getItemManager().loadHabboItem(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (this.itemCount() > Room.MAXIMUM_FURNI) {
            LOGGER.error(
                    "Room ID: {} has exceeded the furniture limit ({} > {}).",
                    this.room.getId(),
                    this.itemCount(),
                    Room.MAXIMUM_FURNI);
        }
    }

    /**
     * Loads wired data for items.
     */
    public void loadWiredData(Connection connection) {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT id, wired_data FROM items WHERE room_id = ? AND wired_data<>''")) {
            statement.setInt(1, this.room.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int itemId = 0;
                    HabboItem item = null;
                    try {
                        itemId = set.getInt("id");
                        item = this.getHabboItem(itemId);

                        if (item instanceof InteractionWired wired) {
                            wired.loadWiredData(set, this.room);
                        }
                    } catch (Exception exception) {
                        if (item instanceof InteractionWired) {
                            this.registry.quarantineWired(item);
                        }
                        LOGGER.error(
                                "Quarantined malformed wired item room={} item={} type={} cause={}",
                                this.room.getId(),
                                itemId,
                                item == null ? "unknown" : item.getClass().getSimpleName(),
                                exception.getClass().getSimpleName());
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }
    }

    // ==================== ITEM RETRIEVAL ====================

    /**
     * Gets an item by ID.
     */
    public HabboItem getHabboItem(int id) {
        return this.index.get(id);
    }

    /** Returns the current in-room incarnation for an item ID, or zero when absent. */
    public long getItemIncarnation(int id) {
        return this.index.itemIncarnation(id);
    }

    /**
     * Gets the total item count.
     */
    public int itemCount() {
        return this.index.size();
    }

    /**
     * Gets all floor items.
     */
    public Set<HabboItem> getFloorItems() {
        return this.index.floorItems();
    }

    /**
     * Gets all wall items.
     */
    public Set<HabboItem> getWallItems() {
        return this.index.wallItems();
    }

    /**
     * Gets all post-it notes.
     */
    public Set<HabboItem> getPostItNotes() {
        return this.index.postItNotes();
    }

    /**
     * Gets the room items map.
     */
    public Int2ObjectMap<HabboItem> getRoomItems() {
        return this.index.items();
    }

    // ==================== ITEM POSITION QUERIES ====================

    /**
     * Gets items at a position (deprecated version using int).
     */
    @Deprecated
    public Set<HabboItem> getItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getItemsAt(tile);
        }

        return new HashSet<>(0);
    }

    /**
     * Gets items at a tile.
     */
    public Set<HabboItem> getItemsAt(RoomTile tile) {
        return getItemsAt(tile, false);
    }

    /**
     * Gets items at a tile with option to return on first match.
     */
    public Set<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
        return this.index.itemsAt(tile, returnOnFirst);
    }

    /**
     * Gets items at a position above a minimum Z height.
     */
    public Set<HabboItem> getItemsAt(int x, int y, double minZ) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.getZ() < minZ) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Gets items of a specific type at a position.
     */
    public Set<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (!item.getClass().equals(type)) {
                continue;
            }

            items.add(item);
        }
        return items;
    }

    /**
     * Checks if there are items at a position.
     */
    public boolean hasItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return false;
        }

        return this.getItemsAt(tile, true).size() > 0;
    }

    /**
     * Gets the top item at a position.
     */
    public HabboItem getTopItemAt(int x, int y) {
        return this.getTopItemAt(x, y, null);
    }

    /**
     * Gets the top item at a position excluding a specific item.
     */
    public HabboItem getTopItemAt(int x, int y, HabboItem exclude) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return null;
        }

        HabboItem highestItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (exclude != null && exclude == item) {
                continue;
            }

            if (highestItem != null
                    && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                            > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    /**
     * Gets the top walkable item at a position, considering underpass.
     * If the topmost item is elevated enough to walk under, returns the highest item at walk surface level instead.
     */
    public HabboItem getWalkableItemAt(int x, int y) {
        HabboItem topItem = this.getTopItemAt(x, y);
        if (topItem == null) {
            return null;
        }

        // If underpass is disabled for this room, just return the top item
        if (!this.room.isAllowUnderpass()) {
            return topItem;
        }

        // If the top item is walkable, just return it
        if (topItem.isWalkable()
                || topItem.getBaseItem().allowWalk()
                || topItem.getBaseItem().allowSit()
                || topItem.getBaseItem().allowLay()) {
            return topItem;
        }

        // Check for underpass: get the walk surface height
        double walkSurface =
                this.room.getLayout() != null ? this.room.getLayout().getHeightAtSquare(x, y) : 0;
        HabboItem walkSurfaceItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.isWalkable()
                    || item.getBaseItem().allowWalk()
                    || item.getBaseItem().allowSit()
                    || item.getBaseItem().allowLay()) {
                double itemTop = item.getZ() + Item.getCurrentHeight(item);
                if (itemTop > walkSurface) {
                    walkSurface = itemTop;
                    walkSurfaceItem = item;
                }
            }
        }

        // If there's enough clearance under the top blocking item, return the walk surface item
        if (topItem.getZ() - walkSurface >= RoomLayout.UNDERPASS_HEIGHT) {
            return walkSurfaceItem;
        }

        return topItem;
    }

    /**
     * Gets the top item from a set of tiles.
     */
    public HabboItem getTopItemAt(Set<RoomTile> tiles, HabboItem exclude) {
        HabboItem highestItem = null;
        for (RoomTile tile : tiles) {

            if (tile == null) {
                continue;
            }

            for (HabboItem item : this.getItemsAt(tile.x, tile.y)) {
                if (exclude != null && exclude == item) {
                    continue;
                }

                if (highestItem != null
                        && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                                > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                highestItem = item;
            }
        }

        return highestItem;
    }

    /**
     * Gets the top height at a position including items.
     */
    public double getTopHeightAt(int x, int y) {
        HabboItem item = this.getTopItemAt(x, y);

        if (item != null) {
            return (item.getZ()
                    + Item.getCurrentHeight(item)
                    - (item.getBaseItem().allowSit() ? 1 : 0));
        } else {
            return this.room.getLayout().getHeightAtSquare(x, y);
        }
    }

    /**
     * Gets the lowest chair at a position.
     */
    @Deprecated
    public HabboItem getLowestChair(int x, int y) {
        if (this.room.getLayout() == null) {
            return null;
        }

        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getLowestChair(tile);
        }

        return null;
    }

    /**
     * Gets the lowest chair at a tile.
     */
    public HabboItem getLowestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        Set<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() < item.getZ()) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    /**
     * Gets the tallest chair at a tile.
     */
    public HabboItem getTallestChair(RoomTile tile) {
        HabboItem lowestChair = null;

        Set<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null
                        && lowestChair.getZ() + Item.getCurrentHeight(lowestChair)
                                > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                lowestChair = item;
            }
        }

        return lowestChair;
    }

    // ==================== ITEM MANIPULATION ====================

    /**
     * Adds an item to the room.
     */
    public void addHabboItem(HabboItem item) {
        this.ownership.add(item);
    }

    /**
     * Removes an item by ID.
     */
    public void removeHabboItem(int id) {
        this.removeHabboItem(this.getHabboItem(id));
    }

    /**
     * Removes an item from the room.
     */
    public void removeHabboItem(HabboItem item) {
        this.ownership.remove(item);
    }

    // ==================== ITEM UPDATES ====================

    /**
     * Updates an item's display.
     */
    public void updateItem(HabboItem item) {
        this.operations.updateItem(item);
    }

    /**
     * Updates an item's state.
     */
    public void updateItemState(HabboItem item) {
        this.operations.updateItemState(item);
    }

    // ==================== FURNITURE OWNER MANAGEMENT ====================

    /**
     * Gets furniture owner names map.
     */
    public Int2ObjectMap<String> getFurniOwnerNames() {
        return this.index.ownerNames();
    }

    /**
     * Gets furniture owner count map.
     */
    public Int2IntMap getFurniOwnerCount() {
        return this.index.ownerCounts();
    }

    /**
     * Gets the username for a furniture owner.
     */
    public String getFurniOwnerName(int oduserId) {
        return this.index.ownerNames().get(oduserId);
    }

    /**
     * Gets the furniture count for a user.
     */
    public int getUserFurniCount(int userId) {
        return this.index.ownerCounts().get(userId);
    }

    /**
     * Gets the unique furniture count for a user.
     */
    public int getUserUniqueFurniCount(int userId) {
        return this.ownership.uniqueItemCount(userId);
    }

    // ==================== PICKUP AND EJECT ====================

    /**
     * Picks up an item from the room.
     */
    public void pickUpItem(HabboItem item, Habbo picker) {
        this.ownership.pickUp(item, picker);
    }

    /**
     * Ejects all furniture belonging to a user.
     */
    public void ejectUserFurni(int userId) {
        this.ownership.ejectUserFurniture(userId);
    }

    /**
     * Ejects a single user item.
     */
    public void ejectUserItem(HabboItem item) {
        this.ownership.pickUp(item, null);
    }

    /**
     * Ejects all items from the room.
     */
    public void ejectAll() {
        this.ejectAll(null);
    }

    /**
     * Ejects all items from the room except those belonging to the specified Habbo.
     */
    public void ejectAll(Habbo habbo) {
        this.ownership.ejectAll(habbo);
    }

    // ==================== LOCKED TILES ====================

    /**
     * Gets all tiles that are locked by furniture.
     */
    public Set<RoomTile> getLockedTiles() {
        Set<RoomTile> lockedTiles = new HashSet<>();

        synchronized (this.index.items()) {
            for (HabboItem item : this.index.items().values()) {
                if (item.getBaseItem().getType() != FurnitureType.FLOOR) {
                    continue;
                }

                boolean found = false;
                for (RoomTile tile : lockedTiles) {
                    if (tile.x == item.getX() && tile.y == item.getY()) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    if (item.getRotation() == 0 || item.getRotation() == 4) {
                        for (short y = 0; y < item.getBaseItem().getLength(); y++) {
                            for (short x = 0; x < item.getBaseItem().getWidth(); x++) {
                                RoomTile tile = this.room.getLayout().getTile((short) (item.getX() + x), (short)
                                        (item.getY() + y));

                                if (tile != null) {
                                    lockedTiles.add(tile);
                                }
                            }
                        }
                    } else {
                        for (short y = 0; y < item.getBaseItem().getWidth(); y++) {
                            for (short x = 0; x < item.getBaseItem().getLength(); x++) {
                                RoomTile tile = this.room.getLayout().getTile((short) (item.getX() + x), (short)
                                        (item.getY() + y));

                                if (tile != null) {
                                    lockedTiles.add(tile);
                                }
                            }
                        }
                    }
                }
            }
        }

        return lockedTiles;
    }

    // ==================== DISPOSAL ====================

    /**
     * Saves all items that need updates to the database.
     */
    public void saveAllPendingItems() {
        List<HabboItem> pendingItems;
        synchronized (this.index.items()) {
            pendingItems = this.index.items().values().stream()
                    .filter(HabboItem::needsUpdate)
                    .toList();
        }

        this.room.savePendingItems(pendingItems);
    }

    /**
     * Clears the item manager state.
     */
    public void clear() {
        this.index.clear();
    }

    /**
     * Disposes the item manager.
     */
    public void dispose() {
        this.clear();
    }

    // ==================== FURNITURE PLACEMENT ====================

    /**
     * Checks if an item has a certain object type at a position.
     */
    public boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        return this.placement.hasObjectTypeAt(type, x, y);
    }

    /**
     * Checks if furniture can be placed at a position.
     */
    public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        return this.placement.canPlaceFurnitureAt(item, habbo, tile, rotation);
    }

    /**
     * Checks if furniture fits at a location.
     */
    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
        return furnitureFitsAt(tile, item, rotation, true);
    }

    /**
     * Checks if furniture fits at a location with unit check option.
     */
    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        return this.placement.furnitureFitsAt(tile, item, rotation, checkForUnits);
    }

    public FurnitureMovementError furnitureFitsAtWithPhysics(
            RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, WiredMovementPhysics physics) {
        return this.movement.furnitureFitsAtWithPhysics(tile, item, rotation, checkForUnits, physics);
    }

    /**
     * Places a floor furniture item at a position.
     */
    public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        return this.placement.placeFloorFurniture(item, tile, rotation, owner);
    }

    /**
     * Places a wall furniture item at a position.
     */
    public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
        return this.placement.placeWallFurniture(item, wallPosition, owner);
    }

    /**
     * Moves furniture to a new position with an explicit Z height.
     */
    public FurnitureMovementError moveFurniTo(
            HabboItem item,
            RoomTile tile,
            int rotation,
            double z,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits) {
        return this.movement.moveFurniTo(item, tile, rotation, z, actor, sendUpdates, checkForUnits);
    }

    public FurnitureMovementError moveFurniToWithPhysics(
            HabboItem item,
            RoomTile tile,
            int rotation,
            double z,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits,
            WiredMovementPhysics physics) {
        return this.movement.moveFurniToWithPhysics(
                item, tile, rotation, z, actor, sendUpdates, checkForUnits, physics);
    }

    /**
     * Moves furniture to a new position.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return this.movement.moveFurniTo(item, tile, rotation, actor);
    }

    /**
     * Moves furniture to a new position with send updates option.
     */
    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return this.movement.moveFurniTo(item, tile, rotation, actor, sendUpdates);
    }

    /**
     * Moves furniture to a new position with full options.
     */
    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        return this.movement.moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
    }

    public FurnitureMovementError moveFurniToWithPhysics(
            HabboItem item,
            RoomTile tile,
            int rotation,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits,
            WiredMovementPhysics physics) {
        return this.movement.moveFurniToWithPhysics(item, tile, rotation, actor, sendUpdates, checkForUnits, physics);
    }

    /**
     * Slides furniture to a new position.
     */
    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        return this.movement.slideFurniTo(item, tile, rotation);
    }
}
