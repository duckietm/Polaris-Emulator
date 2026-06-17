package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.ICycleable;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameGate;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameScoreboard;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiSphere;
import com.eu.habbo.habbohotel.items.interactions.games.battlebanzai.InteractionBattleBanzaiTeleporter;
import com.eu.habbo.habbohotel.items.interactions.games.freeze.InteractionFreezeExitTile;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagField;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagPole;
import com.eu.habbo.habbohotel.items.interactions.pets.*;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.*;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboManager;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.items.*;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.*;
import gnu.trove.TCollections;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import org.apache.commons.math3.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all items/furniture within a room.
 * Handles loading, adding, removing, querying, and picking up items.
 */
public class RoomItemManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomItemManager.class);

    private final Room room;

    // Item storage
    private final TIntObjectMap<HabboItem> roomItems;

    // Furniture owner tracking
    private final TIntObjectMap<String> furniOwnerNames;
    private final TIntIntMap furniOwnerCount;

    // Tile cache for item lookups
    public final ConcurrentHashMap<RoomTile, THashSet<HabboItem>> tileCache;

    public RoomItemManager(Room room) {
        this.room = room;
        this.roomItems = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
        this.furniOwnerNames = TCollections.synchronizedMap(new TIntObjectHashMap<>(0));
        this.furniOwnerCount = TCollections.synchronizedMap(new TIntIntHashMap(0));
        this.tileCache = new ConcurrentHashMap<>();
    }

    // ==================== LOADING ====================

    /**
     * Loads items from the database.
     */
    public void loadItems(Connection connection) {
        synchronized (this.roomItems) {
            this.roomItems.clear();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM items WHERE room_id = ?")) {
            statement.setInt(1, this.room.getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.addHabboItem(Emulator.getGameEnvironment().getItemManager().loadHabboItem(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        if (this.itemCount() > Room.MAXIMUM_FURNI) {
            LOGGER.error("Room ID: {} has exceeded the furniture limit ({} > {}).",
                    this.room.getId(), this.itemCount(), Room.MAXIMUM_FURNI);
        }
    }

    /**
     * Loads wired data for items.
     */
    public void loadWiredData(Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, wired_data FROM items WHERE room_id = ? AND wired_data<>''")) {
            statement.setInt(1, this.room.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        HabboItem item = this.getHabboItem(set.getInt("id"));

                        if (item instanceof InteractionWired) {
                            ((InteractionWired) item).loadWiredData(set, this.room);
                        }
                    } catch (SQLException e) {
                        LOGGER.error("Caught SQL exception", e);
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
        if (this.roomItems == null || this.room.getRoomSpecialTypes() == null) {
            return null;
        }

        HabboItem item;
        synchronized (this.roomItems) {
            item = this.roomItems.get(id);
        }

        if (item == null) {
            item = this.room.getRoomSpecialTypes().getSpecialItem(id);
        }

        return item;
    }

    /**
     * Gets the total item count.
     */
    public int itemCount() {
        return this.roomItems.size();
    }

    /**
     * Gets all floor items.
     */
    public THashSet<HabboItem> getFloorItems() {
        THashSet<HabboItem> items = new THashSet<>();
        // roomItems is a TCollections.synchronizedMap; its iterator is not safe
        // against concurrent put/remove (item place/pickup), so hold the map
        // monitor for the whole traversal, matching the mutation sites.
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (iterator.value().getBaseItem().getType() == FurnitureType.FLOOR) {
                    items.add(iterator.value());
                }
            }
        }

        return items;
    }

    /**
     * Gets all wall items.
     */
    public THashSet<HabboItem> getWallItems() {
        THashSet<HabboItem> items = new THashSet<>();
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (iterator.value().getBaseItem().getType() == FurnitureType.WALL) {
                    items.add(iterator.value());
                }
            }
        }

        return items;
    }

    /**
     * Gets all post-it notes.
     */
    public THashSet<HabboItem> getPostItNotes() {
        THashSet<HabboItem> items = new THashSet<>();
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (iterator.value().getBaseItem().getInteractionType().getType()
                        == InteractionPostIt.class) {
                    items.add(iterator.value());
                }
            }
        }

        return items;
    }

    /**
     * Gets the room items map.
     */
    public TIntObjectMap<HabboItem> getRoomItems() {
        return this.roomItems;
    }

    // ==================== ITEM POSITION QUERIES ====================

    /**
     * Gets items at a position (deprecated version using int).
     */
    @Deprecated
    public THashSet<HabboItem> getItemsAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile != null) {
            return this.getItemsAt(tile);
        }

        return new THashSet<>(0);
    }

    /**
     * Gets items at a tile.
     */
    public THashSet<HabboItem> getItemsAt(RoomTile tile) {
        return getItemsAt(tile, false);
    }

    /**
     * Gets items at a tile with option to return on first match.
     */
    public THashSet<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
        THashSet<HabboItem> items = new THashSet<>(0);

        if (tile == null) {
            return items;
        }

        if (this.room.isLoaded()) {
            THashSet<HabboItem> cachedItems = this.tileCache.get(tile);
            if (cachedItems != null) {
                return cachedItems;
            }
        }

        // Cache miss: iterate roomItems under its monitor so a concurrent
        // place/pickup can't rehash the map mid-traversal (which the per-advance
        // try/catch would otherwise silently swallow into an incomplete result).
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                HabboItem item;
                try {
                    iterator.advance();
                    item = iterator.value();
                } catch (Exception e) {
                    break;
                }

                if (item == null) {
                    continue;
                }

                if (item.getBaseItem().getType() != FurnitureType.FLOOR) {
                    continue;
                }

                int width, length;

                if (item.getRotation() != 2 && item.getRotation() != 6) {
                    width = item.getBaseItem().getWidth() > 0 ? item.getBaseItem().getWidth() : 1;
                    length = item.getBaseItem().getLength() > 0 ? item.getBaseItem().getLength() : 1;
                } else {
                    width = item.getBaseItem().getLength() > 0 ? item.getBaseItem().getLength() : 1;
                    length = item.getBaseItem().getWidth() > 0 ? item.getBaseItem().getWidth() : 1;
                }

                if (!(tile.x >= item.getX() && tile.x <= item.getX() + width - 1 && tile.y >= item.getY()
                        && tile.y <= item.getY() + length - 1)) {
                    continue;
                }

                items.add(item);

                if (returnOnFirst) {
                    return items;
                }
            }
        }

        if (this.room.isLoaded()) {
            this.tileCache.put(tile, items);
        }

        return items;
    }

    /**
     * Gets items at a position above a minimum Z height.
     */
    public THashSet<HabboItem> getItemsAt(int x, int y, double minZ) {
        THashSet<HabboItem> items = new THashSet<>();

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
    public THashSet<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
        THashSet<HabboItem> items = new THashSet<>();

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

            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
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
        if (topItem.isWalkable() || topItem.getBaseItem().allowWalk() || topItem.getBaseItem().allowSit() || topItem.getBaseItem().allowLay()) {
            return topItem;
        }

        // Check for underpass: get the walk surface height
        double walkSurface = this.room.getLayout() != null ? this.room.getLayout().getHeightAtSquare(x, y) : 0;
        HabboItem walkSurfaceItem = null;

        for (HabboItem item : this.getItemsAt(x, y)) {
            if (item.isWalkable() || item.getBaseItem().allowWalk() || item.getBaseItem().allowSit() || item.getBaseItem().allowLay()) {
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
    public HabboItem getTopItemAt(THashSet<RoomTile> tiles, HabboItem exclude) {
        HabboItem highestItem = null;
        for (RoomTile tile : tiles) {

            if (tile == null) {
                continue;
            }

            for (HabboItem item : this.getItemsAt(tile.x, tile.y)) {
                if (exclude != null && exclude == item) {
                    continue;
                }

                if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
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
            return (item.getZ() + Item.getCurrentHeight(item) - (item.getBaseItem().allowSit() ? 1 : 0));
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

        THashSet<HabboItem> items = this.getItemsAt(tile);
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

        THashSet<HabboItem> items = this.getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {

                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (lowestChair != null && lowestChair.getZ() + Item.getCurrentHeight(lowestChair)
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
        if (item == null) {
            return;
        }

        synchronized (this.roomItems) {
            try {
                this.roomItems.put(item.getId(), item);
            } catch (Exception e) {
                // Ignore
            }
        }

        if (BuildersClubRoomSupport.isTrackedItem(item.getId()) && item.getUserId() != BuildersClubRoomSupport.VIRTUAL_OWNER_ID) {
            item.setVirtualUserId(BuildersClubRoomSupport.VIRTUAL_OWNER_ID);
            item.needsUpdate(true);
        }

        synchronized (this.furniOwnerCount) {
            this.furniOwnerCount.put(item.getUserId(), this.furniOwnerCount.get(item.getUserId()) + 1);
        }

        synchronized (this.furniOwnerNames) {
            if (!this.furniOwnerNames.containsKey(item.getUserId())) {
                if (item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID && BuildersClubRoomSupport.isTrackedItem(item.getId())) {
                    this.furniOwnerNames.put(item.getUserId(), BuildersClubRoomSupport.DISPLAY_OWNER_NAME);
                } else {
                    HabboInfo habbo = HabboManager.getOfflineHabboInfo(item.getUserId());

                    if (habbo != null) {
                        this.furniOwnerNames.put(item.getUserId(), habbo.getUsername());
                    } else {
                        LOGGER.error("Failed to find username for item (ID: {}, UserID: {})",
                                item.getId(), item.getUserId());
                    }
                }
            }
        }

        // Register with special types
        this.registerItemWithSpecialTypes(item);
    }

    /**
     * Registers an item with room special types.
     */
    private void registerItemWithSpecialTypes(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        boolean isWiredItem = false;

        synchronized (specialTypes) {
            // Register with tick service for time-based wired triggers (new 50ms tick system)
            // This replaces ICycleable for wired items
            if (item instanceof WiredTickable) {
                WiredManager.registerTickable(this.room, (WiredTickable) item);
            }
            // Still register non-wired ICycleable items with the old system
            else if (item instanceof ICycleable) {
                specialTypes.addCycleTask((ICycleable) item);
            }

            if (item instanceof InteractionWiredTrigger) {
                specialTypes.addTrigger((InteractionWiredTrigger) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredEffect) {
                specialTypes.addEffect((InteractionWiredEffect) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredCondition) {
                specialTypes.addCondition((InteractionWiredCondition) item);
                isWiredItem = true;
            } else if (item instanceof InteractionWiredExtra) {
                specialTypes.addExtra((InteractionWiredExtra) item);
                isWiredItem = true;
            } else if (item instanceof InteractionBattleBanzaiTeleporter) {
                specialTypes.addBanzaiTeleporter((InteractionBattleBanzaiTeleporter) item);
            } else if (item instanceof InteractionRoller) {
                specialTypes.addRoller((InteractionRoller) item);
            } else if (item instanceof InteractionGameScoreboard) {
                specialTypes.addGameScoreboard((InteractionGameScoreboard) item);
            } else if (item instanceof InteractionGameGate) {
                specialTypes.addGameGate((InteractionGameGate) item);
            } else if (item instanceof InteractionGameTimer) {
                specialTypes.addGameTimer((InteractionGameTimer) item);
            } else if (item instanceof InteractionFreezeExitTile) {
                specialTypes.addFreezeExitTile((InteractionFreezeExitTile) item);
            } else if (item instanceof InteractionNest) {
                specialTypes.addNest((InteractionNest) item);
            } else if (item instanceof InteractionPetDrink) {
                specialTypes.addPetDrink((InteractionPetDrink) item);
            } else if (item instanceof InteractionPetFood) {
                specialTypes.addPetFood((InteractionPetFood) item);
            } else if (item instanceof InteractionPetToy) {
                specialTypes.addPetToy((InteractionPetToy) item);
            } else if (item instanceof InteractionPetTree) {
                specialTypes.addPetTree((InteractionPetTree) item);
            } else if (item instanceof InteractionMoodLight ||
                    item instanceof InteractionPyramid ||
                    item instanceof InteractionMusicDisc ||
                    item instanceof InteractionBattleBanzaiSphere ||
                    item instanceof InteractionTalkingFurniture ||
                    item instanceof InteractionWater ||
                    item instanceof InteractionWaterItem ||
                    item instanceof InteractionMuteArea ||
                    item instanceof InteractionBuildArea ||
                    item instanceof InteractionTagPole ||
                    item instanceof InteractionTagField ||
                    item instanceof InteractionJukeBox ||
                    item instanceof InteractionPetBreedingNest ||
                    item instanceof InteractionBlackHole ||
                    item instanceof InteractionWiredHighscore ||
                    item instanceof InteractionStickyPole ||
                    item instanceof WiredBlob ||
                    item instanceof InteractionTent ||
                    item instanceof InteractionSnowboardSlope ||
                    item instanceof InteractionFireworks || item instanceof InteractionVoteCounter) {
                specialTypes.addUndefined(item);
            }
        }

        // Invalidate wired cache when wired items are added
        if (isWiredItem) {
            WiredManager.invalidateRoom(this.room);
        }
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
        if (item == null) {
            return;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());
        int trackedUserId = trackedBuildersClubItem ? BuildersClubRoomSupport.getTrackedUserId(item.getId()) : item.getUserId();

        HabboItem i;
        synchronized (this.roomItems) {
            i = this.roomItems.remove(item.getId());
        }

        if (i != null) {
            synchronized (this.furniOwnerCount) {
                synchronized (this.furniOwnerNames) {
                    int count = this.furniOwnerCount.get(i.getUserId());

                    if (count > 1) {
                        this.furniOwnerCount.put(i.getUserId(), count - 1);
                    } else {
                        this.furniOwnerCount.remove(i.getUserId());
                        this.furniOwnerNames.remove(i.getUserId());
                    }
                }
            }

            // Unregister from special types
            this.unregisterItemFromSpecialTypes(item);
        }

        if (trackedBuildersClubItem) {
            BuildersClubRoomSupport.deleteTrackedItem(item.getId());

            if (BuildersClubRoomSupport.syncRoom(this.room) == BuildersClubRoomSupport.SyncResult.UNLOCKED) {
                BuildersClubRoomSupport.sendRoomUnlockedBubble(this.room.getOwnerId());
            }

            BuildersClubRoomSupport.sendPlacementStatusForPool(this.room, trackedUserId);
        }
    }

    /**
     * Unregisters an item from room special types.
     */
    private void unregisterItemFromSpecialTypes(HabboItem item) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return;
        }

        boolean cleanedSignalAntennaReferences = false;
        if (this.isAntennaItem(item)) {
            cleanedSignalAntennaReferences = specialTypes.unlinkSignalAntennaReferences(item.getId());
        }

        this.room.getFurniVariableManager().removeAssignmentsForFurni(item.getId());

        boolean isWiredItem = false;

        // Unregister from tick service for time-based wired triggers (new 50ms tick system)
        if (item instanceof WiredTickable) {
            WiredManager.unregisterTickable(this.room, (WiredTickable) item);
        }
        // Still handle non-wired ICycleable items with the old system
        else if (item instanceof ICycleable) {
            specialTypes.removeCycleTask((ICycleable) item);
        }

        if (item instanceof InteractionBattleBanzaiTeleporter) {
            specialTypes.removeBanzaiTeleporter((InteractionBattleBanzaiTeleporter) item);
        } else if (item instanceof InteractionWiredTrigger) {
            specialTypes.removeTrigger((InteractionWiredTrigger) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredEffect) {
            specialTypes.removeEffect((InteractionWiredEffect) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredCondition) {
            specialTypes.removeCondition((InteractionWiredCondition) item);
            isWiredItem = true;
        } else if (item instanceof InteractionWiredExtra) {
            boolean removedContextDefinition = false;
            boolean removedVariableTextConnector = false;
            if (item instanceof WiredExtraUserVariable) {
                this.room.getUserVariableManager().removeDefinition(item.getId());
            } else if (item instanceof WiredExtraFurniVariable) {
                this.room.getFurniVariableManager().removeDefinition(item.getId());
            } else if (item instanceof WiredExtraRoomVariable) {
                this.room.getRoomVariableManager().removeDefinition(item.getId());
            } else if (item instanceof WiredExtraContextVariable) {
                removedContextDefinition = true;
            } else if (item instanceof WiredExtraVariableTextConnector) {
                removedVariableTextConnector = true;
            } else if (item instanceof WiredExtraVariableReference) {
                if (((WiredExtraVariableReference) item).isRoomReference()) {
                    this.room.getRoomVariableManager().removeDefinition(item.getId());
                } else {
                    this.room.getUserVariableManager().removeDefinition(item.getId());
                }
            } else if (item instanceof WiredExtraVariableEcho) {
                WiredExtraVariableEcho echo = (WiredExtraVariableEcho) item;

                if (echo.isRoomEcho()) {
                    this.room.getRoomVariableManager().removeDefinition(item.getId());
                } else if (echo.isFurniEcho()) {
                    this.room.getFurniVariableManager().removeDefinition(item.getId());
                } else {
                    this.room.getUserVariableManager().removeDefinition(item.getId());
                }
            }
            specialTypes.removeExtra((InteractionWiredExtra) item);
            if (removedContextDefinition || removedVariableTextConnector) {
                WiredContextVariableSupport.broadcastDefinitions(this.room);
            }
            isWiredItem = true;
        } else if (item instanceof InteractionRoller) {
            specialTypes.removeRoller((InteractionRoller) item);
        } else if (item instanceof InteractionGameScoreboard) {
            specialTypes.removeScoreboard((InteractionGameScoreboard) item);
        } else if (item instanceof InteractionGameGate) {
            specialTypes.removeGameGate((InteractionGameGate) item);
        } else if (item instanceof InteractionGameTimer) {
            specialTypes.removeGameTimer((InteractionGameTimer) item);
        } else if (item instanceof InteractionFreezeExitTile) {
            specialTypes.removeFreezeExitTile((InteractionFreezeExitTile) item);
        } else if (item instanceof InteractionNest) {
            specialTypes.removeNest((InteractionNest) item);
        } else if (item instanceof InteractionPetDrink) {
            specialTypes.removePetDrink((InteractionPetDrink) item);
        } else if (item instanceof InteractionPetFood) {
            specialTypes.removePetFood((InteractionPetFood) item);
        } else if (item instanceof InteractionPetToy) {
            specialTypes.removePetToy((InteractionPetToy) item);
        } else if (item instanceof InteractionPetTree) {
            specialTypes.removePetTree((InteractionPetTree) item);
        } else if (item instanceof InteractionMoodLight ||
                item instanceof InteractionPyramid ||
                item instanceof InteractionMusicDisc ||
                item instanceof InteractionBattleBanzaiSphere ||
                item instanceof InteractionTalkingFurniture ||
                item instanceof InteractionWaterItem ||
                item instanceof InteractionWater ||
                item instanceof InteractionMuteArea ||
                item instanceof InteractionTagPole ||
                item instanceof InteractionTagField ||
                item instanceof InteractionJukeBox ||
                item instanceof InteractionPetBreedingNest ||
                item instanceof InteractionBlackHole ||
                item instanceof InteractionWiredHighscore ||
                item instanceof InteractionStickyPole ||
                item instanceof WiredBlob ||
                item instanceof InteractionTent ||
                item instanceof InteractionSnowboardSlope || item instanceof InteractionVoteCounter) {
            specialTypes.removeUndefined(item);
        }

        // Invalidate wired cache when wired items are removed
        if (isWiredItem || cleanedSignalAntennaReferences) {
            WiredManager.invalidateRoom(this.room);
        }
    }

    private boolean isAntennaItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionType = item.getBaseItem().getInteractionType().getName();
        return interactionType != null && interactionType.equalsIgnoreCase("antenna");
    }

    // ==================== ITEM UPDATES ====================

    /**
     * Updates an item's display.
     */
    public void updateItem(HabboItem item) {
        if (this.room.isLoaded()) {
            if (item != null && item.getRoomId() == this.room.getId()) {
                if (item.getBaseItem() != null) {
                    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
                        this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
                        this.room.updateTiles(this.room.getLayout()
                                .getTilesAt(this.room.getLayout().getTile(item.getX(), item.getY()),
                                        item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                                        item.getRotation()));
                    } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
                        this.room.sendComposer(new WallItemUpdateComposer(item).compose());
                    }
                }
            }
        }
    }

    /**
     * Updates an item's state.
     */
    public void updateItemState(HabboItem item) {
        if (!item.isLimited()) {
            this.room.sendComposer(new ItemStateComposer(item).compose());
        } else {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            if (this.room.getLayout() == null) {
                return;
            }

            this.room.updateTiles(this.room.getLayout()
                    .getTilesAt(this.room.getLayout().getTile(item.getX(), item.getY()),
                            item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                            item.getRotation()));

            if (item instanceof InteractionMultiHeight) {
                ((InteractionMultiHeight) item).updateUnitsOnItem(this.room);
            }
        }
    }

    // ==================== FURNITURE OWNER MANAGEMENT ====================

    /**
     * Gets furniture owner names map.
     */
    public TIntObjectMap<String> getFurniOwnerNames() {
        return this.furniOwnerNames;
    }

    /**
     * Gets furniture owner count map.
     */
    public TIntIntMap getFurniOwnerCount() {
        return this.furniOwnerCount;
    }

    /**
     * Gets the username for a furniture owner.
     */
    public String getFurniOwnerName(int oduserId) {
        return this.furniOwnerNames.get(oduserId);
    }

    /**
     * Gets the furniture count for a user.
     */
    public int getUserFurniCount(int userId) {
        return this.furniOwnerCount.get(userId);
    }

    /**
     * Gets the unique furniture count for a user.
     */
    public int getUserUniqueFurniCount(int userId) {
        THashSet<Item> items = new THashSet<>();

        synchronized (this.roomItems) {
            for (HabboItem item : this.roomItems.valueCollection()) {
                if (!items.contains(item.getBaseItem()) && item.getUserId() == userId) {
                    items.add(item.getBaseItem());
                }
            }
        }

        return items.size();
    }

    // ==================== PICKUP AND EJECT ====================

    /**
     * Picks up an item from the room.
     */
    public void pickUpItem(HabboItem item, Habbo picker) {
        if (item == null) {
            return;
        }

        boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());

        if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
            FurniturePickedUpEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurniturePickedUpEvent(item, picker));

            if (event.isCancelled()) {
                return;
            }
        }

        this.removeHabboItem(item);
        item.onPickUp(this.room);
        item.setRoomId(0);
        item.needsUpdate(true);

        if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.room.sendComposer(new RemoveFloorItemComposer(item).compose());

            if (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item)) {
                RoomConfInvisSupport.sendState(this.room);
            }

            if (RoomAreaHideSupport.isControllerItem(item)) {
                RoomAreaHideSupport.sendState(this.room, item);
            }

            if (RoomHanditemBlockSupport.isControllerItem(item)) {
                RoomHanditemBlockSupport.sendState(this.room);
            }

            THashSet<RoomTile> updatedTiles = this.room.getLayout().getTilesAt(
                    this.room.getLayout().getTile(item.getX(), item.getY()),
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    item.getRotation());
            this.room.updateTiles(updatedTiles);

            for (RoomTile tile : updatedTiles) {
                this.room.updateHabbosAt(tile.x, tile.y);
                this.room.updateBotsAt(tile.x, tile.y);
            }
        } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.room.sendComposer(new RemoveWallItemComposer(item).compose());
        }

        if (trackedBuildersClubItem) {
            Emulator.getGameEnvironment().getItemManager().deleteItem(item);
            return;
        }

        Emulator.getThreading().run(item);
    }

    /**
     * Ejects all furniture belonging to a user.
     */
    public void ejectUserFurni(int userId) {
        THashSet<HabboItem> items = new THashSet<>();
        THashSet<HabboItem> inventoryItems = new THashSet<>();

        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            try {
                iterator.advance();
            } catch (Exception e) {
                break;
            }

            if (iterator.value().getUserId() == userId) {
                items.add(iterator.value());

                if (!BuildersClubRoomSupport.isTrackedItem(iterator.value().getId())) {
                    inventoryItems.add(iterator.value());
                }

                iterator.value().setRoomId(0);
            }
        }

        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (habbo != null && !inventoryItems.isEmpty()) {
            habbo.getInventory().getItemsComponent().addItems(inventoryItems);
            habbo.getClient().sendResponse(new AddHabboItemComposer(inventoryItems));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
        }

        for (HabboItem i : items) {
            this.pickUpItem(i, null);
        }
    }

    /**
     * Ejects a single user item.
     */
    public void ejectUserItem(HabboItem item) {
        this.pickUpItem(item, null);
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
        THashMap<Integer, THashSet<HabboItem>> userItemsMap = new THashMap<>();

        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();
                } catch (Exception e) {
                    break;
                }

                if (habbo != null && iterator.value().getUserId() == habbo.getHabboInfo().getId()) {
                    continue;
                }

                if (iterator.value() instanceof InteractionPostIt) {
                    continue;
                }

                userItemsMap.computeIfAbsent(iterator.value().getUserId(), k -> new THashSet<>())
                        .add(iterator.value());
            }
        }

        for (Map.Entry<Integer, THashSet<HabboItem>> entrySet : userItemsMap.entrySet()) {
            THashSet<HabboItem> inventoryItems = new THashSet<>();

            for (HabboItem item : entrySet.getValue()) {
                if (!BuildersClubRoomSupport.isTrackedItem(item.getId())) {
                    inventoryItems.add(item);
                }
            }

            for (HabboItem i : entrySet.getValue()) {
                this.pickUpItem(i, null);
            }

            Habbo user = Emulator.getGameEnvironment().getHabboManager().getHabbo(entrySet.getKey());

            if (user != null && !inventoryItems.isEmpty()) {
                user.getInventory().getItemsComponent().addItems(inventoryItems);
                user.getClient().sendResponse(new AddHabboItemComposer(inventoryItems));
                user.getClient().sendResponse(new InventoryRefreshComposer());
            }
        }
    }

    // ==================== LOCKED TILES ====================

    /**
     * Gets all tiles that are locked by furniture.
     */
    public THashSet<RoomTile> getLockedTiles() {
        THashSet<RoomTile> lockedTiles = new THashSet<>();

        TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

        for (int i = this.roomItems.size(); i-- > 0; ) {
            HabboItem item;
            try {
                iterator.advance();
                item = iterator.value();
            } catch (Exception e) {
                break;
            }

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
                            RoomTile tile = this.room.getLayout().getTile(
                                    (short) (item.getX() + x), (short) (item.getY() + y));

                            if (tile != null) {
                                lockedTiles.add(tile);
                            }
                        }
                    }
                } else {
                    for (short y = 0; y < item.getBaseItem().getWidth(); y++) {
                        for (short x = 0; x < item.getBaseItem().getLength(); x++) {
                            RoomTile tile = this.room.getLayout().getTile(
                                    (short) (item.getX() + x), (short) (item.getY() + y));

                            if (tile != null) {
                                lockedTiles.add(tile);
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
        synchronized (this.roomItems) {
            TIntObjectIterator<HabboItem> iterator = this.roomItems.iterator();

            for (int i = this.roomItems.size(); i-- > 0; ) {
                try {
                    iterator.advance();

                    if (iterator.value().needsUpdate()) {
                        iterator.value().run();
                    }
                } catch (java.util.NoSuchElementException e) {
                    break;
                }
            }
        }
    }

    /**
     * Clears the item manager state.
     */
    public void clear() {
        synchronized (this.roomItems) {
            this.roomItems.clear();
        }
        synchronized (this.furniOwnerCount) {
            this.furniOwnerCount.clear();
        }
        synchronized (this.furniOwnerNames) {
            this.furniOwnerNames.clear();
        }
        this.tileCache.clear();
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
        THashSet<HabboItem> items = this.getItemsAt(x, y);

        for (HabboItem item : items) {
            if (item.getClass() == type) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks if furniture can be placed at a position.
     */
    public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        if (this.itemCount() >= Room.MAXIMUM_FURNI) {
            return FurnitureMovementError.MAX_ITEMS;
        }

        if (tile == null || tile.state == RoomTileState.INVALID) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        rotation %= 8;
        if (this.room.hasRights(habbo) || this.room.getGuildRightLevel(habbo)
                .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS) || habbo.hasPermission(
                Permission.ACC_MOVEROTATE) || BuildersClubRoomSupport.canPlaceInRoom(habbo, this.room)) {
            return FurnitureMovementError.NONE;
        }

        if (habbo.getHabboStats().isRentingSpace()) {
            HabboItem rentSpace = this.getHabboItem(habbo.getHabboStats().rentedItemId);

            if (rentSpace != null) {
                if (!RoomLayout.squareInSquare(RoomLayout.getRectangle(rentSpace.getX(), rentSpace.getY(),
                                rentSpace.getBaseItem().getWidth(), rentSpace.getBaseItem().getLength(),
                                rentSpace.getRotation()),
                        RoomLayout.getRectangle(tile.x, tile.y, item.getBaseItem().getWidth(),
                                item.getBaseItem().getLength(), rotation))) {
                    return FurnitureMovementError.NO_RIGHTS;
                } else {
                    return FurnitureMovementError.NONE;
                }
            }
        }

        for (HabboItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
            if (((InteractionBuildArea) area).inSquare(tile) && ((InteractionBuildArea) area).isBuilder(
                    habbo.getHabboInfo().getUsername())) {
                return FurnitureMovementError.NONE;
            }
        }

        return FurnitureMovementError.NO_RIGHTS;
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
    private boolean isStackPlacementBypassItem(HabboItem item) {
        return item instanceof InteractionStackHelper
                || item instanceof InteractionTileWalkMagic
                || item instanceof InteractionStackWalkHelper;
    }

    private boolean shouldPinStackHelperToFloor(HabboItem item) {
        return item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic;
    }

    private boolean isStackHeightHelper(HabboItem item) {
        return item instanceof InteractionStackHelper
                || item instanceof InteractionTileWalkMagic
                || item instanceof InteractionStackWalkHelper;
    }

    private HabboItem findStackHeightHelperAt(RoomTile tile, HabboItem exclude) {
        if (tile == null) {
            return null;
        }

        for (HabboItem helper : this.getItemsAt(tile)) {
            if (helper != exclude && this.isStackHeightHelper(helper)) {
                return helper;
            }
        }

        return null;
    }

    private double getMinimumTileHeight(THashSet<RoomTile> occupiedTiles) {
        double minimumHeight = 0.0D;

        for (RoomTile occupiedTile : occupiedTiles) {
            minimumHeight = Math.max(minimumHeight, this.room.getLayout().getHeightAtSquare(occupiedTile.x, occupiedTile.y));
        }

        return minimumHeight;
    }

    private double getConfiguredStackWalkHelperHeight(HabboItem item, THashSet<RoomTile> occupiedTiles) {
        double height = 0.0D;

        try {
            if (item.getExtradata() != null && !item.getExtradata().isEmpty()) {
                height = Double.parseDouble(item.getExtradata()) / 100.0D;
            }
        } catch (NumberFormatException ignored) {
        }

        return Math.max(height, this.getMinimumTileHeight(occupiedTiles));
    }

    private double resolveStackWalkHelperHeight(HabboItem item, RoomTile tile, THashSet<RoomTile> occupiedTiles) {
        HabboItem helper = this.findStackHeightHelperAt(tile, item);
        if (helper != null) {
            return Math.max(helper.getZ(), this.getMinimumTileHeight(occupiedTiles));
        }

        return this.getConfiguredStackWalkHelperHeight(item, occupiedTiles);
    }

    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (this.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        for (RoomTile t : occupiedTiles) {
            if (t.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }
            if (!Emulator.getConfig().getBoolean("wired.place.under", false) || (
                    Emulator.getConfig().getBoolean("wired.place.under", false) && !item.isWalkable()
                            && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay())) {
                if (checkForUnits && this.room.hasHabbosAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_HABBOS;
                }
                if (checkForUnits && this.room.hasBotsAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_BOTS;
                }
                if (checkForUnits && this.room.hasPetsAt(t.x, t.y)) {
                    return FurnitureMovementError.TILE_HAS_PETS;
                }
            }
        }

        java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getItemsAt(t)));

            HabboItem topItem = this.getTopItemAt(t.x, t.y, item);
            if (topItem != null && !topItem.getBaseItem().allowStack() && !t.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        if (!item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError furnitureFitsAtWithPhysics(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return furnitureFitsAt(tile, item, rotation, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (this.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        for (RoomTile t : occupiedTiles) {
            if (t.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }

            if (shouldCheckUnits(item, checkForUnits)) {
                FurnitureMovementError unitCollision = this.getPhysicsUnitCollision(t, physics);
                if (unitCollision != FurnitureMovementError.NONE) {
                    return unitCollision;
                }
            }
        }

        if (this.hasBlockingPhysicsFurni(occupiedTiles, item, physics)) {
            return FurnitureMovementError.CANT_STACK;
        }

        java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getPhysicsItemsAt(t, item, physics)));

            HabboItem topItem = this.getTopPhysicsItemAt(t.x, t.y, item, physics);
            if (topItem != null && !topItem.getBaseItem().allowStack() && !t.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        if (!item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        return FurnitureMovementError.NONE;
    }

    /**
     * Places a floor furniture item at a position.
     */
    public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes != null) {
            if (item instanceof WiredEffectSendSignal && specialTypes.isSignalSenderLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_SENDERS;
            }
            if (item instanceof WiredTriggerReceiveSignal && specialTypes.isSignalReceiverLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_RECEIVERS;
            }
        }

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            FurniturePlacedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurniturePlacedEvent(item, owner, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        RoomLayout layout = this.room.getLayout();
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);

        FurnitureMovementError fits = furnitureFitsAt(tile, item, rotation);

        if (!fits.equals(FurnitureMovementError.NONE) && !pluginHelper) {
            return fits;
        }

        double height = tile.getStackHeight();

        for (RoomTile tile2 : occupiedTiles) {
            double sHeight = tile2.getStackHeight();
            if (sHeight > height) {
                height = sHeight;
            }
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, owner, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setZ(height);
        item.setX(tile.x);
        item.setY(tile.y);
        item.setRotation(rotation);
        if (!this.furniOwnerNames.containsKey(item.getUserId()) && owner != null) {
            this.furniOwnerNames.put(item.getUserId(), this.resolveOwnerName(item, owner));
        }

        item.needsUpdate(true);
        this.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        this.room.updateTiles(occupiedTiles);
        this.room.sendComposer(
                new AddFloorItemComposer(item, this.getFurniOwnerName(item.getUserId())).compose());

        if (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item)) {
            RoomConfInvisSupport.sendState(this.room);
        }

        if (RoomAreaHideSupport.isControllerItem(item)) {
            RoomAreaHideSupport.sendState(this.room, item);
        }

        if (RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }

        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    /**
     * Places a wall furniture item at a position.
     */
    public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
        if (!(this.room.hasRights(owner) || this.room.getGuildRightLevel(owner)
                .isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS) || BuildersClubRoomSupport.canPlaceInRoom(owner, this.room))) {
            return FurnitureMovementError.NO_RIGHTS;
        }

        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            Event furniturePlacedEvent = new FurniturePlacedEvent(item, owner, null);
            Emulator.getPluginManager().fireEvent(furniturePlacedEvent);

            if (furniturePlacedEvent.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
        }

        item.setWallPosition(wallPosition);
        if (!this.furniOwnerNames.containsKey(item.getUserId()) && owner != null) {
            this.furniOwnerNames.put(item.getUserId(), this.resolveOwnerName(item, owner));
        }
        this.room.sendComposer(
                new AddWallItemComposer(item, this.getFurniOwnerName(item.getUserId())).compose());
        item.needsUpdate(true);
        this.addHabboItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    private String resolveOwnerName(HabboItem item, Habbo owner) {
        if (item != null && item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID) {
            return BuildersClubRoomSupport.DISPLAY_OWNER_NAME;
        }

        return (owner != null) ? owner.getHabboInfo().getUsername() : "";
    }

    /**
     * Moves furniture to a new position with an explicit Z height.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        if (item == null || tile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;

        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.isStackPlacementBypassItem(item);

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(
                tile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation
        );

        THashSet<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation()
        );

        if (!pluginHelper) {
            FurnitureMovementError fits = furnitureFitsAt(tile, item, rotation, checkForUnits);
            if (fits != FurnitureMovementError.NONE) {
                return fits;
            }
        }

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);

            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event rotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(rotatedEvent);

                if (rotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }
        }

        // Height sanity checks
        if (z > Room.MAXIMUM_FURNI_HEIGHT) {
            return FurnitureMovementError.CANT_STACK;
        }

        // Prevent furni going under the floor
        if (z < layout.getHeightAtSquare(tile.x, tile.y)) {
            return FurnitureMovementError.CANT_STACK;
        }

        // Plugin height override (match your NEW behavior: base + updatedHeight)
        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index + invalidate cache
        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            }
        }

        // Update furniture
        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        // Update old & new tiles
        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        // Update Habbos/Bots
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }

        // Preserve your newer "place under" behavior if enabled
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            THashSet<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile,
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    rotation
            );

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) { }
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return moveFurniTo(item, tile, rotation, z, actor, sendUpdates, checkForUnits);
        }

        if (item == null || tile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;

        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.isStackPlacementBypassItem(item);

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(
                tile,
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                rotation
        );

        THashSet<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation()
        );

        if (!pluginHelper) {
            FurnitureMovementError fits = furnitureFitsAtWithPhysics(tile, item, rotation, checkForUnits, physics);
            if (fits != FurnitureMovementError.NONE) {
                return fits;
            }
        }

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);

            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event rotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(rotatedEvent);

                if (rotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }
        }

        if (z > Room.MAXIMUM_FURNI_HEIGHT) {
            return FurnitureMovementError.CANT_STACK;
        }

        if (z < layout.getHeightAtSquare(tile.x, tile.y)) {
            return FurnitureMovementError.CANT_STACK;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            }
        }

        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }

        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            THashSet<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile,
                    item.getBaseItem().getWidth(),
                    item.getBaseItem().getLength(),
                    rotation
            );

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        return FurnitureMovementError.NONE;
    }

    /**
     * Moves furniture to a new position.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return moveFurniTo(item, tile, rotation, actor, true, true);
    }

    /**
     * Moves furniture to a new position with send updates option.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, true);
    }

    /**
     * Moves furniture to a new position with full options.
     */
    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.findStackHeightHelperAt(tile, item);

        // Check if can be placed at new position
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        THashSet<RoomTile> newOccupiedTiles = layout.getTilesAt(tile,
                item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopItemAt(occupiedTiles, null);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopItemAt(t.x, t.y);
                    if (!magicTile && ((tileTopItem != null && tileTopItem != item ? (
                            t.state.equals(RoomTileState.INVALID) || !t.getAllowStack()
                            || !tileTopItem.getBaseItem().allowStack())
                            : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (!Emulator.getConfig().getBoolean("wired.place.under", false) || (
                            Emulator.getConfig().getBoolean("wired.place.under", false) && !item.isWalkable()
                                    && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay())) {
                        if (checkForUnits) {
                            if (!magicTile && this.room.hasHabbosAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_HABBOS;
                            }
                            if (!magicTile && this.room.hasBotsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_BOTS;
                            }
                            if (!magicTile && this.room.hasPetsAt(t.x, t.y)) {
                                return FurnitureMovementError.TILE_HAS_PETS;
                            }
                        }
                    }
                }
            }

            java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        THashSet<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), item.getRotation());

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);
            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(furnitureRotatedEvent);

                if (furnitureRotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }

            if ((stackHelper == null && topItem != null && topItem != item && !topItem.getBaseItem()
                    .allowStack()) || (topItem != null && topItem != item
                    && topItem.getZ() + Item.getCurrentHeight(topItem) + Item.getCurrentHeight(item)
                    > Room.MAXIMUM_FURNI_HEIGHT)) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANT_STACK;
            }
        }

        // Place at new position
        double height;

        if (stackHelper != null) {
            height = stackHelper.getZ();
        } else if (item instanceof InteractionStackWalkHelper) {
            height = this.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
        } else if (item == topItem) {
            height = item.getZ();
        } else if (magicTile) {
            if (topItem == null) {
                height = this.room.getStackHeight(tile.x, tile.y, false, item);
                for (RoomTile til : occupiedTiles) {
                    double sHeight = this.room.getStackHeight(til.x, til.y, false, item);
                    if (sHeight > height) {
                        height = sHeight;
                    }
                }
            } else {
                height = topItem.getZ() + topItem.getBaseItem().getHeight();
            }
        } else {
            height = this.room.getStackHeight(tile.x, tile.y, false, item);
            for (RoomTile til : occupiedTiles) {
                double sHeight = this.room.getStackHeight(til.x, til.y, false, item);
                if (sHeight > height) {
                    height = sHeight;
                }
            }
        }

        boolean cantStack = false;
        boolean pluginHeight = false;

        if (height > Room.MAXIMUM_FURNI_HEIGHT) {
            cantStack = true;
        }
        if (height < layout.getHeightAtSquare(tile.x, tile.y)) {
            cantStack = true;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
                pluginHeight = true;
            }
        }

        if (!pluginHeight && cantStack) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index and invalidate cache when wired items are moved
        if (item instanceof InteractionWiredTrigger) {
            this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        }

        // Update Furniture
        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        // Update old & new tiles
        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        }
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.findStackHeightHelperAt(tile, item);

        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);
        THashSet<RoomTile> newOccupiedTiles = layout.getTilesAt(tile,
                item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopPhysicsItemAt(occupiedTiles, null, physics);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopPhysicsItemAt(t.x, t.y, item, physics);
                    if (!magicTile && ((tileTopItem != null && tileTopItem != item ? (
                            t.state.equals(RoomTileState.INVALID) || !t.getAllowStack()
                            || !tileTopItem.getBaseItem().allowStack())
                            : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (shouldCheckUnits(item, checkForUnits)) {
                        FurnitureMovementError unitCollision = this.getPhysicsUnitCollision(t, physics);
                        if (!magicTile && unitCollision != FurnitureMovementError.NONE) {
                            return unitCollision;
                        }
                    }
                }
            }

            if (this.hasBlockingPhysicsFurni(occupiedTiles, item, physics)) {
                return FurnitureMovementError.CANT_STACK;
            }

            java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getPhysicsItemsAt(t, item, physics)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        THashSet<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), item.getRotation());

        int oldRotation = item.getRotation();

        if (oldRotation != rotation) {
            item.setRotation(rotation);
            if (Emulator.getPluginManager().isRegistered(FurnitureRotatedEvent.class, true)) {
                Event furnitureRotatedEvent = new FurnitureRotatedEvent(item, actor, oldRotation);
                Emulator.getPluginManager().fireEvent(furnitureRotatedEvent);

                if (furnitureRotatedEvent.isCancelled()) {
                    item.setRotation(oldRotation);
                    return FurnitureMovementError.CANCEL_PLUGIN_ROTATE;
                }
            }

            if ((stackHelper == null && topItem != null && topItem != item && !topItem.getBaseItem()
                    .allowStack()) || (topItem != null && topItem != item
                    && topItem.getZ() + Item.getCurrentHeight(topItem) + Item.getCurrentHeight(item)
                    > Room.MAXIMUM_FURNI_HEIGHT)) {
                item.setRotation(oldRotation);
                return FurnitureMovementError.CANT_STACK;
            }
        }

        double height;

        if (stackHelper != null) {
            height = stackHelper.getZ();
        } else if (item instanceof InteractionStackWalkHelper) {
            height = this.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
        } else if (item == topItem) {
            height = item.getZ();
        } else if (magicTile) {
            if (topItem == null) {
                height = this.getPhysicsStackHeight(tile.x, tile.y, item, physics);
                for (RoomTile til : occupiedTiles) {
                    double sHeight = this.getPhysicsStackHeight(til.x, til.y, item, physics);
                    if (sHeight > height) {
                        height = sHeight;
                    }
                }
            } else {
                height = topItem.getZ() + topItem.getBaseItem().getHeight();
            }
        } else {
            height = this.getPhysicsStackHeight(tile.x, tile.y, item, physics);
            for (RoomTile til : occupiedTiles) {
                double sHeight = this.getPhysicsStackHeight(til.x, til.y, item, physics);
                if (sHeight > height) {
                    height = sHeight;
                }
            }
        }

        boolean cantStack = false;
        boolean pluginHeight = false;

        if (height > Room.MAXIMUM_FURNI_HEIGHT) {
            cantStack = true;
        }
        if (height < layout.getHeightAtSquare(tile.x, tile.y)) {
            cantStack = true;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event = Emulator.getPluginManager()
                    .fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
                pluginHeight = true;
            }
        }

        if (!pluginHeight && cantStack) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(height);
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (item instanceof InteractionWiredTrigger) {
            this.room.getRoomSpecialTypes().updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room.getRoomSpecialTypes().updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room.getRoomSpecialTypes().updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room.getRoomSpecialTypes().updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        }

        item.onMove(this.room, oldLocation, tile);
        item.needsUpdate(true);
        Emulator.getThreading().run(item);

        if (sendUpdates) {
            this.room.sendComposer(new FloorItemUpdateComposer(item).compose());
        }

        occupiedTiles.removeAll(oldOccupiedTiles);
        occupiedTiles.addAll(oldOccupiedTiles);
        this.room.updateTiles(occupiedTiles);

        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y, this.room.getHabbosAt(t.x, t.y));
            this.room.updateBotsAt(t.x, t.y);
        }
        if (Emulator.getConfig().getBoolean("wired.place.under", false)) {
            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception e) {
                    }
                }
            }
        }
        return FurnitureMovementError.NONE;
    }

    /**
     * Slides furniture to a new position.
     */
    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        boolean magicTile = this.isStackPlacementBypassItem(item);

        RoomLayout layout = this.room.getLayout();

        // Check if can be placed at new position
        THashSet<RoomTile> occupiedTiles = layout.getTilesAt(tile, item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(), rotation);

        java.util.List<Pair<RoomTile, THashSet<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.getItemsAt(t)));
        }

        if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setRotation(rotation);

        // Place at new position
        if (this.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }
        double offset = this.room.getStackHeight(tile.x, tile.y, false, item) - item.getZ();
        this.room.sendComposer(new FloorItemOnRollerComposer(item, null, tile, offset, this.room).compose());

        // Update Habbos at old position
        for (RoomTile t : occupiedTiles) {
            this.room.updateHabbosAt(t.x, t.y);
            this.room.updateBotsAt(t.x, t.y);
        }
        return FurnitureMovementError.NONE;
    }

    private boolean shouldCheckUnits(HabboItem item, boolean checkForUnits) {
        if (!checkForUnits) {
            return false;
        }

        if (!Emulator.getConfig().getBoolean("wired.place.under", false)) {
            return true;
        }

        return !item.isWalkable()
                && !item.getBaseItem().allowSit()
                && !item.getBaseItem().allowLay();
    }

    private FurnitureMovementError getPhysicsUnitCollision(RoomTile tile, WiredMovementPhysics physics) {
        for (RoomUnit roomUnit : this.room.getRoomUnits(tile)) {
            if (roomUnit == null) {
                continue;
            }

            switch (roomUnit.getRoomUnitType()) {
                case BOT:
                    return FurnitureMovementError.TILE_HAS_BOTS;
                case PET:
                    return FurnitureMovementError.TILE_HAS_PETS;
                case USER:
                    if (physics == null || !physics.shouldIgnoreUser(roomUnit)) {
                        return FurnitureMovementError.TILE_HAS_HABBOS;
                    }
                    break;
                default:
                    return FurnitureMovementError.TILE_HAS_HABBOS;
            }
        }

        return FurnitureMovementError.NONE;
    }

    private boolean hasBlockingPhysicsFurni(THashSet<RoomTile> occupiedTiles, HabboItem exclude, WiredMovementPhysics physics) {
        if (physics == null || !physics.hasBlockingFurni()) {
            return false;
        }

        for (RoomTile tile : occupiedTiles) {
            for (HabboItem item : this.getItemsAt(tile)) {
                if (item == null || item == exclude) {
                    continue;
                }

                if (physics.isBlockingFurni(item)) {
                    return true;
                }
            }
        }

        return false;
    }

    private THashSet<HabboItem> getPhysicsItemsAt(RoomTile tile, HabboItem exclude, WiredMovementPhysics physics) {
        THashSet<HabboItem> items = new THashSet<>();

        for (HabboItem item : this.getItemsAt(tile)) {
            if (item == null || item == exclude) {
                continue;
            }

            if (physics != null && physics.shouldIgnoreFurni(item)) {
                continue;
            }

            items.add(item);
        }

        return items;
    }

    private HabboItem getTopPhysicsItemAt(int x, int y, HabboItem exclude, WiredMovementPhysics physics) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);

        if (tile == null) {
            return null;
        }

        HabboItem highestItem = null;

        for (HabboItem item : this.getPhysicsItemsAt(tile, exclude, physics)) {
            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    private HabboItem getTopPhysicsItemAt(THashSet<RoomTile> tiles, HabboItem exclude, WiredMovementPhysics physics) {
        HabboItem highestItem = null;

        for (RoomTile tile : tiles) {
            if (tile == null) {
                continue;
            }

            HabboItem topItem = this.getTopPhysicsItemAt(tile.x, tile.y, exclude, physics);
            if (topItem == null) {
                continue;
            }

            if (highestItem != null && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                    > topItem.getZ() + Item.getCurrentHeight(topItem)) {
                continue;
            }

            highestItem = topItem;
        }

        return highestItem;
    }

    private double getPhysicsStackHeight(short x, short y, HabboItem exclude, WiredMovementPhysics physics) {
        RoomLayout layout = this.room.getLayout();

        if (x < 0 || y < 0 || layout == null) {
            return 0.0;
        }

        double height = layout.getHeightAtSquare(x, y);

        RoomTile tile = layout.getTile(x, y);
        if (tile == null) {
            return height;
        }

        double helperHeight = Double.NEGATIVE_INFINITY;
        for (HabboItem item : this.getPhysicsItemsAt(tile, exclude, physics)) {
            if (item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper) {
                helperHeight = Math.max(helperHeight, item.getZ());
            }
        }

        if (helperHeight != Double.NEGATIVE_INFINITY) {
            return helperHeight;
        }

        HabboItem topItem = this.getTopPhysicsItemAt(x, y, exclude, physics);
        if (topItem != null) {
            return topItem.getZ() + (topItem.getBaseItem().allowSit() ? 0 : Item.getCurrentHeight(topItem));
        }

        return height;
    }
}
