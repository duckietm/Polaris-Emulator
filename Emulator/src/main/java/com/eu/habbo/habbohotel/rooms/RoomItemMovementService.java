package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionStackWalkHelper;
import com.eu.habbo.habbohotel.items.interactions.InteractionTileWalkMagic;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureBuildheightEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureMovedEvent;
import com.eu.habbo.plugin.events.furniture.FurnitureRotatedEvent;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.math3.util.Pair;

final class RoomItemMovementService {

    private final Room room;
    private final RoomItemManager facade;
    private final RoomItemPlacementService placement;

    RoomItemMovementService(Room room, RoomItemManager facade, RoomItemPlacementService placement) {
        this.room = room;
        this.facade = facade;
        this.placement = placement;
    }

    public FurnitureMovementError furnitureFitsAtWithPhysics(
            RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return this.placement.furnitureFitsAt(tile, item, rotation, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        if (this.placement.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
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

        java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
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

    public FurnitureMovementError moveFurniTo(
            HabboItem item,
            RoomTile tile,
            int rotation,
            double z,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits) {
        if (item == null || tile == null) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;

        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.placement.isStackPlacementBypassItem(item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());

        if (!pluginHelper) {
            FurnitureMovementError fits = this.placement.furnitureFitsAt(tile, item, rotation, checkForUnits);
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
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.placement.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index + invalidate cache
        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room
                        .getRoomSpecialTypes()
                        .updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room
                        .getRoomSpecialTypes()
                        .updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room
                        .getRoomSpecialTypes()
                        .updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room
                        .getRoomSpecialTypes()
                        .updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
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
            Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        this.room.onFurnitureTopologyChanged();

        return FurnitureMovementError.NONE;
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
            FurnitureMovedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));

            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }

            pluginHelper = event.hasPluginHelper();
        }

        rotation %= 8;

        boolean magicTile = this.placement.isStackPlacementBypassItem(item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
                layout.getTile(item.getX(), item.getY()),
                item.getBaseItem().getWidth(),
                item.getBaseItem().getLength(),
                item.getRotation());

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
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, z));

            if (event.hasChangedHeight()) {
                z = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setX(tile.x);
        item.setY(tile.y);
        item.setZ(z);

        if (this.placement.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + (item.getZ() * 100));
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
        }

        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (oldLocation != null) {
            if (item instanceof InteractionWiredTrigger) {
                this.room
                        .getRoomSpecialTypes()
                        .updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredEffect) {
                this.room
                        .getRoomSpecialTypes()
                        .updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredCondition) {
                this.room
                        .getRoomSpecialTypes()
                        .updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
                WiredManager.invalidateRoom(this.room);
            } else if (item instanceof InteractionWiredExtra) {
                this.room
                        .getRoomSpecialTypes()
                        .updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
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
            Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                    tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

            for (RoomTile t : newOccupiedTiles) {
                for (Habbo h : this.room.getHabbosAt(t.x, t.y)) {
                    try {
                        item.onWalkOn(h.getRoomUnit(), this.room, null);
                    } catch (Exception ignored) {
                    }
                }
            }
        }

        this.room.onFurnitureTopologyChanged();
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return moveFurniTo(item, tile, rotation, actor, true, true);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return moveFurniTo(item, tile, rotation, actor, sendUpdates, true);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.placement.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.placement.findStackHeightHelperAt(tile, item);

        // Check if can be placed at new position
        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.facade.getTopItemAt(occupiedTiles, null);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.facade.getTopItemAt(t.x, t.y);
                    if (!magicTile
                            && ((tileTopItem != null && tileTopItem != item
                                    ? (t.state.equals(RoomTileState.INVALID)
                                            || !t.getAllowStack()
                                            || !tileTopItem.getBaseItem().allowStack())
                                    : this.room.calculateTileState(t, item).equals(RoomTileState.INVALID)))) {
                        return FurnitureMovementError.CANT_STACK;
                    }

                    if (!Emulator.getConfig().getBoolean("wired.place.under", false)
                            || (Emulator.getConfig().getBoolean("wired.place.under", false)
                                    && !item.isWalkable()
                                    && !item.getBaseItem().allowSit()
                                    && !item.getBaseItem().allowLay())) {
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

            java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.facade.getItemsAt(t)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
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

            if ((stackHelper == null
                            && topItem != null
                            && topItem != item
                            && !topItem.getBaseItem().allowStack())
                    || (topItem != null
                            && topItem != item
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
            height = this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
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
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
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
        if (this.placement.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        // Update wired spatial index and invalidate cache when wired items are moved
        if (item instanceof InteractionWiredTrigger) {
            this.room
                    .getRoomSpecialTypes()
                    .updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room
                    .getRoomSpecialTypes()
                    .updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room
                    .getRoomSpecialTypes()
                    .updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room
                    .getRoomSpecialTypes()
                    .updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
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
        this.room.onFurnitureTopologyChanged();
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError moveFurniToWithPhysics(
            HabboItem item,
            RoomTile tile,
            int rotation,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits,
            WiredMovementPhysics physics) {
        if (physics == null || !physics.isActive()) {
            return moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
        }

        RoomLayout layout = this.room.getLayout();
        RoomTile oldLocation = layout.getTile(item.getX(), item.getY());

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurnitureMovedEvent.class, true)) {
            FurnitureMovedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureMovedEvent(item, actor, oldLocation, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_MOVE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        boolean magicTile = this.placement.isStackPlacementBypassItem(item);

        HabboItem stackHelper = this.placement.findStackHeightHelperAt(tile, item);

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        Set<RoomTile> newOccupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        HabboItem topItem = this.getTopPhysicsItemAt(occupiedTiles, null, physics);

        if (stackHelper == null && !pluginHelper) {
            if (oldLocation != tile) {
                for (RoomTile t : occupiedTiles) {
                    HabboItem tileTopItem = this.getTopPhysicsItemAt(t.x, t.y, item, physics);
                    if (!magicTile
                            && ((tileTopItem != null && tileTopItem != item
                                    ? (t.state.equals(RoomTileState.INVALID)
                                            || !t.getAllowStack()
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

            java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
            for (RoomTile t : occupiedTiles) {
                tileFurniList.add(Pair.create(t, this.getPhysicsItemsAt(t, item, physics)));
            }

            if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        Set<RoomTile> oldOccupiedTiles = layout.getTilesAt(
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

            if ((stackHelper == null
                            && topItem != null
                            && topItem != item
                            && !topItem.getBaseItem().allowStack())
                    || (topItem != null
                            && topItem != item
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
            height = this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles);
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
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, actor, 0.00, height));
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
        if (this.placement.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        }
        if (item.getZ() > Room.MAXIMUM_FURNI_HEIGHT) {
            item.setZ(Room.MAXIMUM_FURNI_HEIGHT);
        }

        if (item instanceof InteractionWiredTrigger) {
            this.room
                    .getRoomSpecialTypes()
                    .updateTriggerLocation((InteractionWiredTrigger) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredEffect) {
            this.room
                    .getRoomSpecialTypes()
                    .updateEffectLocation((InteractionWiredEffect) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredCondition) {
            this.room
                    .getRoomSpecialTypes()
                    .updateConditionLocation((InteractionWiredCondition) item, oldLocation.x, oldLocation.y);
            WiredManager.invalidateRoom(this.room);
        } else if (item instanceof InteractionWiredExtra) {
            this.room
                    .getRoomSpecialTypes()
                    .updateExtraLocation((InteractionWiredExtra) item, oldLocation.x, oldLocation.y);
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
        this.room.onFurnitureTopologyChanged();
        return FurnitureMovementError.NONE;
    }

    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        boolean magicTile = this.placement.isStackPlacementBypassItem(item);

        RoomLayout layout = this.room.getLayout();

        // Check if can be placed at new position
        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);

        java.util.List<Pair<RoomTile, Set<HabboItem>>> tileFurniList = new java.util.ArrayList<>();
        for (RoomTile t : occupiedTiles) {
            tileFurniList.add(Pair.create(t, this.facade.getItemsAt(t)));
        }

        if (!magicTile && !item.canStackAt(this.room, tileFurniList)) {
            return FurnitureMovementError.CANT_STACK;
        }

        item.setRotation(rotation);

        // Place at new position
        if (this.placement.shouldPinStackHelperToFloor(item)) {
            item.setZ(tile.z);
            item.setExtradata("" + item.getZ() * 100);
        } else if (item instanceof InteractionStackWalkHelper) {
            item.setZ(this.placement.resolveStackWalkHelperHeight(item, tile, occupiedTiles));
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
        this.room.onFurnitureTopologyChanged();
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

    private boolean hasBlockingPhysicsFurni(
            Set<RoomTile> occupiedTiles, HabboItem exclude, WiredMovementPhysics physics) {
        if (physics == null || !physics.hasBlockingFurni()) {
            return false;
        }

        for (RoomTile tile : occupiedTiles) {
            for (HabboItem item : this.facade.getItemsAt(tile)) {
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

    private Set<HabboItem> getPhysicsItemsAt(RoomTile tile, HabboItem exclude, WiredMovementPhysics physics) {
        Set<HabboItem> items = new HashSet<>();

        for (HabboItem item : this.facade.getItemsAt(tile)) {
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
            if (highestItem != null
                    && highestItem.getZ() + Item.getCurrentHeight(highestItem)
                            > item.getZ() + Item.getCurrentHeight(item)) {
                continue;
            }

            highestItem = item;
        }

        return highestItem;
    }

    private HabboItem getTopPhysicsItemAt(Set<RoomTile> tiles, HabboItem exclude, WiredMovementPhysics physics) {
        HabboItem highestItem = null;

        for (RoomTile tile : tiles) {
            if (tile == null) {
                continue;
            }

            HabboItem topItem = this.getTopPhysicsItemAt(tile.x, tile.y, exclude, physics);
            if (topItem == null) {
                continue;
            }

            if (highestItem != null
                    && highestItem.getZ() + Item.getCurrentHeight(highestItem)
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
            if (item instanceof InteractionStackHelper
                    || item instanceof InteractionTileWalkMagic
                    || item instanceof InteractionStackWalkHelper) {
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
