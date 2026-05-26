package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.plugin.events.furniture.FurnitureStackHeightEvent;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages tile state calculations and heightmap operations for a room.
 */
public class RoomTileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomTileManager.class);

    private final Room room;

    public RoomTileManager(Room room) {
        this.room = room;
    }

    /**
     * Updates a single tile's stack height and state.
     */
    public void updateTile(RoomTile tile) {
        if (tile != null) {
            this.room.getItemManager().tileCache.remove(tile);
            tile.setStackHeight(this.getStackHeight(tile.x, tile.y, false));
            tile.setState(this.calculateTileState(tile));
        }
    }

    /**
     * Updates multiple tiles and sends the update to clients.
     */
    public void updateTiles(THashSet<RoomTile> tiles) {
        for (RoomTile tile : tiles) {
            this.room.getItemManager().tileCache.remove(tile);
            tile.setStackHeight(this.getStackHeight(tile.x, tile.y, false));
            tile.setState(this.calculateTileState(tile));
        }

        this.room.sendComposer(new com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer(this.room, tiles).compose());
    }

    /**
     * Calculates the state of a tile based on items on it.
     */
    public RoomTileState calculateTileState(RoomTile tile) {
        return this.calculateTileState(tile, null);
    }

    /**
     * Calculates the state of a tile, optionally excluding an item.
     */
    public RoomTileState calculateTileState(RoomTile tile, HabboItem exclude) {
        if (tile == null || tile.state == RoomTileState.INVALID) {
            return RoomTileState.INVALID;
        }

        RoomTileState result = RoomTileState.OPEN;
        THashSet<HabboItem> items = this.room.getItemManager().getItemsAt(tile);

        if (items == null) {
            return RoomTileState.INVALID;
        }

        HabboItem tallestItem = null;

        for (HabboItem item : items) {
            if (exclude != null && item == exclude) {
                continue;
            }

            if (item.getBaseItem().allowLay()) {
                return RoomTileState.LAY;
            }

            if (tallestItem != null && tallestItem.getZ() + Item.getCurrentHeight(tallestItem)
                > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            result = this.checkStateForItem(item, tile);
            tallestItem = item;
        }

        if (this.room.isAllowUnderpass() && result == RoomTileState.BLOCKED && tallestItem != null) {
            double walkSurface = this.getUnderpassWalkHeight(tile, items, exclude);
            if (tallestItem.getZ() - walkSurface >= RoomLayout.UNDERPASS_HEIGHT) {
                result = RoomTileState.OPEN;
            }
        }

        return result;
    }

    /**
     * Calculates the walk surface height for underpass checks.
     * Returns the floor height or the top of the highest walkable item below any blocking items.
     */
    private double getUnderpassWalkHeight(RoomTile tile, THashSet<HabboItem> items, HabboItem exclude) {
        RoomLayout layout = this.room.getLayout();
        double walkHeight = layout != null ? layout.getHeightAtSquare(tile.x, tile.y) : 0;

        if (items != null) {
            for (HabboItem item : items) {
                if (exclude != null && item == exclude) {
                    continue;
                }
                if (item.isWalkable() || item.getBaseItem().allowWalk() || item.getBaseItem().allowSit() || item.getBaseItem().allowLay()) {
                    double itemTop = item.getZ() + Item.getCurrentHeight(item);
                    if (itemTop > walkHeight) {
                        walkHeight = itemTop;
                    }
                }
            }
        }

        return walkHeight;
    }

    /**
     * Determines the tile state based on a specific item.
     */
    private RoomTileState checkStateForItem(HabboItem item, RoomTile tile) {
        RoomTileState result = RoomTileState.BLOCKED;

        if (item.isWalkable()) {
            result = RoomTileState.OPEN;
        }

        if (item.getBaseItem().allowSit()) {
            result = RoomTileState.SIT;
        }

        if (item.getBaseItem().allowLay()) {
            result = RoomTileState.LAY;
        }

        RoomTileState overriddenState = item.getOverrideTileState(tile, this.room);
        if (overriddenState != null) {
            result = overriddenState;
        }

        if (this.room.getItemManager().getItemsAt(tile).stream().anyMatch(i -> i instanceof InteractionTileWalkMagic || i instanceof InteractionStackWalkHelper)) {
            result = RoomTileState.OPEN;
        }

        return result;
    }

    /**
     * Checks if a tile is walkable.
     */
    public boolean tileWalkable(RoomTile t) {
        return this.tileWalkable(t.x, t.y);
    }

    /**
     * Checks if coordinates are walkable.
     */
    public boolean tileWalkable(short x, short y) {
        RoomLayout layout = this.room.getLayout();
        if (layout == null) return false;
        
        boolean walkable = layout.tileWalkable(x, y);
        RoomTile tile = layout.getTile(x, y);

        if (walkable && tile != null) {
            if (tile.hasUnits() && !this.room.isAllowWalkthrough()) {
                walkable = false;
            }
        }

        return walkable;
    }

    /**
     * Gets the stack height at a position.
     */
    public double getStackHeight(short x, short y, boolean calculateHeightmap) {
        return this.getStackHeight(x, y, calculateHeightmap, null);
    }

    /**
     * Gets the stack height at a position, optionally excluding an item.
     */
    public double getStackHeight(short x, short y, boolean calculateHeightmap, HabboItem exclude) {
        RoomLayout layout = this.room.getLayout();
        
        if (x < 0 || y < 0 || layout == null) {
            return calculateHeightmap ? Short.MAX_VALUE : 0.0;
        }

        if (Emulator.getPluginManager().isRegistered(FurnitureStackHeightEvent.class, true)) {
            FurnitureStackHeightEvent event = Emulator.getPluginManager()
                .fireEvent(new FurnitureStackHeightEvent(x, y, this.room));
            if (event.hasPluginHelper()) {
                return calculateHeightmap ? event.getHeight() * 256.0D : event.getHeight();
            }
        }

        double height = layout.getHeightAtSquare(x, y);
        boolean canStack = true;

        THashSet<HabboItem> stackHelpers = this.room.getItemManager().getItemsAt(InteractionStackHelper.class, x, y);
        stackHelpers.addAll(this.room.getItemManager().getItemsAt(InteractionStackWalkHelper.class, x, y));
        stackHelpers.addAll(this.room.getItemManager().getItemsAt(InteractionTileWalkMagic.class, x, y));

        if (stackHelpers.size() > 0) {
            double helperHeight = Double.NEGATIVE_INFINITY;
            for (HabboItem item : stackHelpers) {
                if (item == exclude) {
                    continue;
                }
                helperHeight = Math.max(helperHeight, item.getZ());
            }

            if (helperHeight != Double.NEGATIVE_INFINITY) {
                return calculateHeightmap ? helperHeight * 256.0D : helperHeight;
            }
        }

        HabboItem item = this.room.getItemManager().getTopItemAt(x, y, exclude);
        if (item != null) {
            canStack = item.getBaseItem().allowStack();
            double itemTop = item.getZ() + (item.getBaseItem().allowSit() ? 0 : Item.getCurrentHeight(item));

            // Underpass: if the top item is blocking but high enough to walk under, use floor height
            if (this.room.isAllowUnderpass() && !item.isWalkable() && !item.getBaseItem().allowWalk() && !item.getBaseItem().allowSit() && !item.getBaseItem().allowLay()) {
                RoomLayout layout2 = this.room.getLayout();
                RoomTile tile = layout2 != null ? layout2.getTile(x, y) : null;
                THashSet<HabboItem> allItems = tile != null ? this.room.getItemManager().getItemsAt(tile) : null;
                double walkSurface = this.getUnderpassWalkHeight(tile, allItems, exclude);
                if (item.getZ() - walkSurface >= RoomLayout.UNDERPASS_HEIGHT) {
                    height = walkSurface;
                } else {
                    height = itemTop;
                }
            } else {
                height = itemTop;
            }
        }

        if (calculateHeightmap) {
            return (canStack ? height * 256.0D : Short.MAX_VALUE);
        }

        return canStack ? height : -1;
    }

    /**
     * Gets the top height at a position.
     */
    public double getTopHeightAt(int x, int y) {
        HabboItem item = this.room.getItemManager().getTopItemAt(x, y);

        if (item != null) {
            return (item.getZ() + Item.getCurrentHeight(item) - (item.getBaseItem().allowSit() ? 1 : 0));
        } else {
            RoomLayout layout = this.room.getLayout();
            return layout != null ? layout.getHeightAtSquare(x, y) : 0;
        }
    }

    /**
     * Gets the lowest chair at a position.
     */
    public HabboItem getLowestChair(int x, int y) {
        RoomLayout layout = this.room.getLayout();
        if (layout == null) {
            return null;
        }

        RoomTile tile = layout.getTile((short) x, (short) y);

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

        THashSet<HabboItem> items = this.room.getItemManager().getItemsAt(tile);
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
        HabboItem tallestChair = null;

        THashSet<HabboItem> items = this.room.getItemManager().getItemsAt(tile);
        if (items != null && !items.isEmpty()) {
            for (HabboItem item : items) {
                if (!item.getBaseItem().allowSit()) {
                    continue;
                }

                if (tallestChair != null && tallestChair.getZ() + Item.getCurrentHeight(tallestChair)
                    > item.getZ() + Item.getCurrentHeight(item)) {
                    continue;
                }

                tallestChair = item;
            }
        }

        return tallestChair;
    }

    /**
     * Checks if a user can sit or lay at a position.
     */
    public boolean canSitOrLayAt(int x, int y) {
        if (this.room.getUnitManager().hasHabbosAt(x, y)) {
            return false;
        }

        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);
        THashSet<HabboItem> items = this.room.getItemManager().getItemsAt(tile);

        return this.canSitAt(items) || this.canLayAt(items);
    }

    /**
     * Checks if a user can sit at a position.
     */
    public boolean canSitAt(int x, int y) {
        if (this.room.getUnitManager().hasHabbosAt(x, y)) {
            return false;
        }

        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);
        return this.canSitAt(this.room.getItemManager().getItemsAt(tile));
    }

    /**
     * Checks if items allow sitting.
     */
    public boolean canSitAt(THashSet<HabboItem> items) {
        if (items == null) {
            return false;
        }

        HabboItem tallestItem = null;

        for (HabboItem item : items) {
            if (tallestItem != null && tallestItem.getZ() + Item.getCurrentHeight(tallestItem)
                > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            tallestItem = item;
        }

        if (tallestItem == null) {
            return false;
        }

        return tallestItem.getBaseItem().allowSit();
    }

    /**
     * Checks if a user can lay at a position.
     */
    public boolean canLayAt(int x, int y) {
        RoomTile tile = this.room.getLayout().getTile((short) x, (short) y);
        return this.canLayAt(this.room.getItemManager().getItemsAt(tile));
    }

    /**
     * Checks if items allow laying.
     */
    public boolean canLayAt(THashSet<HabboItem> items) {
        if (items == null || items.isEmpty()) {
            return true;
        }

        HabboItem topItem = null;

        for (HabboItem item : items) {
            if ((topItem == null || item.getZ() > topItem.getZ())) {
                topItem = item;
            }
        }

        return (topItem == null || topItem.getBaseItem().allowLay());
    }

    /**
     * Checks if a tile can be walked on.
     */
    public boolean canWalkAt(RoomTile roomTile) {
        if (roomTile == null) {
            return false;
        }

        if (roomTile.state == RoomTileState.INVALID) {
            return false;
        }

        HabboItem topItem = null;
        boolean canWalk = true;
        THashSet<HabboItem> items = this.room.getItemManager().getItemsAt(roomTile);
        if (items != null && items.stream().anyMatch(item -> item instanceof InteractionTileWalkMagic || item instanceof InteractionStackWalkHelper)) {
            return true;
        }

        if (items != null) {
            for (HabboItem item : items) {
                if (topItem == null) {
                    topItem = item;
                }

                if (item.getZ() > topItem.getZ()) {
                    topItem = item;
                    canWalk = topItem.isWalkable() || topItem.getBaseItem().allowWalk();
                } else if (item.getZ() == topItem.getZ() && canWalk) {
                    if ((!topItem.isWalkable() && !topItem.getBaseItem().allowWalk()) || (
                        !item.getBaseItem().allowWalk() && !item.isWalkable())) {
                        canWalk = false;
                    }
                }
            }
        }

        // Underpass: if top item blocks but is high enough, allow walking under
        if (this.room.isAllowUnderpass() && !canWalk && topItem != null) {
            double walkSurface = this.getUnderpassWalkHeight(roomTile, items, null);
            if (topItem.getZ() - walkSurface >= RoomLayout.UNDERPASS_HEIGHT) {
                canWalk = true;
            }
        }

        return canWalk;
    }

    /**
     * Gets a random walkable tile in the room.
     */
    public RoomTile getRandomWalkableTile() {
        RoomLayout layout = this.room.getLayout();
        if (layout == null) return null;
        
        for (int i = 0; i < 10; i++) {
            RoomTile tile = layout.getTile((short) (Math.random() * layout.getMapSizeX()),
                (short) (Math.random() * layout.getMapSizeY()));
            if (tile != null && tile.getState() != RoomTileState.BLOCKED
                && tile.getState() != RoomTileState.INVALID) {
                return tile;
            }
        }

        return null;
    }

    /**
     * Gets a random walkable tile around a position within a radius.
     */
    public RoomTile getRandomWalkableTilesAround(RoomUnit roomUnit, RoomTile tile, int radius) {
        RoomLayout layout = this.room.getLayout();
        if (layout == null) return tile;
        
        if (tile == null || !layout.tileExists(tile.x, tile.y)) {
            tile = layout.getTile(roomUnit.getX(), roomUnit.getY());
            roomUnit.setBotStartLocation(tile);
            Bot bot = this.room.getUnitManager().getBot(roomUnit);
            if (bot != null) {
                bot.needsUpdate(true);
            }
        }

        java.util.List<RoomTile> walkableTiles = new java.util.ArrayList<>();

        int minX = Math.max(0, tile.x - radius);
        int minY = Math.max(0, tile.y - radius);
        int maxX = Math.min(layout.getMapSizeX() - 1, tile.x + radius);
        int maxY = Math.min(layout.getMapSizeY() - 1, tile.y + radius);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                RoomTile candidateTile = layout.getTile((short) x, (short) y);

                if (candidateTile != null && candidateTile.getState() != RoomTileState.BLOCKED
                    && candidateTile.getState() != RoomTileState.INVALID) {
                    walkableTiles.add(candidateTile);
                }
            }
        }

        if (walkableTiles.isEmpty()) {
            return tile;
        }

        java.util.Collections.shuffle(walkableTiles);
        return walkableTiles.get(0);
    }

    /**
     * Loads the heightmap for the room.
     * Only updates tiles that have items on them (+ door tile) instead of all tiles,
     * using getTilesAt() to correctly handle rotated multi-tile furniture.
     */
    public void loadHeightmap() {
        RoomLayout layout = this.room.getLayout();
        if (layout != null) {
            THashSet<HabboItem> floorItems = this.room.getFloorItems();

            if (floorItems.isEmpty()) {
                // No items - only update door tile
                RoomTile doorTile = layout.getDoorTile();
                if (doorTile != null) {
                    this.updateTile(doorTile);
                }
                return;
            }

            // Collect unique tiles occupied by items (handles rotation)
            THashSet<RoomTile> tilesToUpdate = new THashSet<>();
            for (HabboItem item : floorItems) {
                RoomTile baseTile = layout.getTile(item.getX(), item.getY());
                if (baseTile != null) {
                    tilesToUpdate.addAll(layout.getTilesAt(baseTile,
                        item.getBaseItem().getWidth(),
                        item.getBaseItem().getLength(),
                        item.getRotation()));
                }
            }

            // Always include door tile
            RoomTile doorTile = layout.getDoorTile();
            if (doorTile != null) {
                tilesToUpdate.add(doorTile);
            }

            for (RoomTile tile : tilesToUpdate) {
                this.updateTile(tile);
            }
        } else {
            LOGGER.error("Unknown Room Layout for Room (ID: {})", this.room.getId());
        }
    }
}
