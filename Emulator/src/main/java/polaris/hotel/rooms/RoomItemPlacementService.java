package polaris.hotel.rooms;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import polaris.Emulator;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.interactions.InteractionBackgroundToner;
import polaris.hotel.items.interactions.InteractionBuildArea;
import polaris.hotel.items.interactions.InteractionCannon;
import polaris.hotel.items.interactions.InteractionPuzzleBox;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.items.interactions.InteractionRoomAds;
import polaris.hotel.items.interactions.InteractionStackHelper;
import polaris.hotel.items.interactions.InteractionStackWalkHelper;
import polaris.hotel.items.interactions.InteractionTileWalkMagic;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.InteractionWiredExtra;
import polaris.hotel.items.interactions.StackHelperExtradata;
import polaris.hotel.items.interactions.wired.effects.WiredEffectSendSignal;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerReceiveSignal;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.core.WiredRoomStats;
import polaris.messages.outgoing.rooms.items.AddFloorItemComposer;
import polaris.messages.outgoing.rooms.items.AddWallItemComposer;
import polaris.plugin.Event;
import polaris.plugin.events.furniture.FurnitureBuildheightEvent;
import polaris.plugin.events.furniture.FurniturePlacedEvent;

final class RoomItemPlacementService {

    private final Room room;
    private final RoomItemIndex index;
    private final RoomItemManager facade;

    RoomItemPlacementService(Room room, RoomItemIndex index, RoomItemManager facade) {
        this.room = room;
        this.index = index;
        this.facade = facade;
    }

    boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        for (UserItem item : this.facade.getItemsAt(x, y)) {
            if (item.getClass() == type) {
                return true;
            }
        }
        return false;
    }

    FurnitureMovementError canPlaceFurnitureAt(UserItem item, User user, RoomTile tile, int rotation) {
        // The room cap is for furni that would enter the room: moving or rotating one already here adds nothing, and a
        // room that is full (or was loaded over the cap) must still be able to rearrange what it holds.
        if (this.facade.getUserItem(item.getId()) == null && this.facade.itemCount() >= Room.MAXIMUM_FURNI) {
            return FurnitureMovementError.MAX_ITEMS;
        }
        if (tile == null || tile.state == RoomTileState.INVALID) {
            return FurnitureMovementError.INVALID_MOVE;
        }

        rotation %= 8;
        if (this.room.hasRights(user)
                || this.room.getGuildRightLevel(user).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)
                || user.hasPermission(Permission.ACC_MOVEROTATE)
                || BuildersClubRoomSupport.canPlaceInRoom(user, this.room)) {
            return FurnitureMovementError.NONE;
        }

        if (user.getUserStats().isRentingSpace()) {
            UserItem rentSpace = this.facade.getUserItem(user.getUserStats().rentedItemId);
            if (rentSpace != null) {
                return tenantMoveInRental(rentSpace, item, user, tile, rotation, this.isInThisRoom(item));
            }
        }

        for (UserItem area : this.room.getRoomSpecialTypes().getItemsOfType(InteractionBuildArea.class)) {
            InteractionBuildArea buildArea = (InteractionBuildArea) area;
            if (buildArea.inSquare(tile)
                    && buildArea.isBuilder(user.getUserInfo().getUsername())) {
                if (!mayTenantMove(item, user)) {
                    return FurnitureMovementError.NO_RIGHTS;
                }
                RoomTile origin = this.room.getLayout() == null
                        ? null
                        : this.room.getLayout().getTile(item.getX(), item.getY());
                if (this.isInThisRoom(item) && (origin == null || !buildArea.inSquare(origin))) {
                    return FurnitureMovementError.NO_RIGHTS;
                }
                return FurnitureMovementError.NONE;
            }
        }
        return FurnitureMovementError.NO_RIGHTS;
    }

    /**
     * What a tenant of a rented space may do with one furni.
     *
     * @param itemIsInRoom whether the furni already stands in this room, rather than coming
     *     out of the tenant's inventory
     */
    static FurnitureMovementError tenantMoveInRental(
            UserItem rentSpace, UserItem item, User user, RoomTile tile, int rotation, boolean itemIsInRoom) {
        if (!mayTenantMove(item, user)) {
            return FurnitureMovementError.NO_RIGHTS;
        }
        if (!insideRental(rentSpace, item, tile.x, tile.y, rotation)) {
            return FurnitureMovementError.NO_RIGHTS;
        }
        // Where it stands now counts too: otherwise the destination check alone let a
        // tenant drag a furni in from the rest of the room.
        if (itemIsInRoom && !insideRental(rentSpace, item, item.getX(), item.getY(), item.getRotation())) {
            return FurnitureMovementError.NO_RIGHTS;
        }
        return FurnitureMovementError.NONE;
    }

    private boolean isInThisRoom(UserItem item) {
        return this.facade.getUserItem(item.getId()) != null;
    }

    /**
     * Whether someone with no rights in the room - a tenant of a rented space, a named
     * builder of a build area - may move this particular furni.
     *
     * <p>Only the destination tile used to be checked, so anyone renting a space could
     * drag someone else's furni, a rare included, across the room into their own square.
     * A tenant moves their own furni, and not the kinds {@code RoomPlaceItemEvent}
     * already refuses to let them place at all.
     */
    static boolean mayTenantMove(UserItem item, User user) {
        if (item.getUserId() != user.getUserInfo().getId()) {
            return false;
        }
        return !isTenantRestrictedType(item);
    }

    /** The same kinds {@code RoomPlaceItemEvent} keeps out of a rental or build area. */
    private static boolean isTenantRestrictedType(UserItem item) {
        return item instanceof InteractionRoller
                || item instanceof InteractionStackHelper
                || item instanceof InteractionStackWalkHelper
                || item instanceof InteractionWired
                || item instanceof InteractionBackgroundToner
                || item instanceof InteractionRoomAds
                || item instanceof InteractionCannon
                || item instanceof InteractionPuzzleBox
                || item.getBaseItem().getType() == FurnitureType.WALL;
    }

    private static boolean insideRental(UserItem rentSpace, UserItem item, int x, int y, int rotation) {
        return RoomLayout.squareInSquare(
                RoomLayout.getRectangle(
                        rentSpace.getX(),
                        rentSpace.getY(),
                        rentSpace.getBaseItem().getWidth(),
                        rentSpace.getBaseItem().getLength(),
                        rentSpace.getRotation()),
                RoomLayout.getRectangle(
                        x, y, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation));
    }

    FurnitureMovementError furnitureFitsAt(RoomTile tile, UserItem item, int rotation, boolean checkForUnits) {
        return this.furnitureFitsAt(tile, item, rotation, checkForUnits, null);
    }

    /**
     * Like {@link #furnitureFitsAt(RoomTile, UserItem, int, boolean)}, but the user placing or moving the furni by
     * hand does not block their own tile: Habbo lets the room owner put a tile or a block where they stand. Other
     * avatars and wired moves still block. Behaviour observed in plus-emulator/PlusEMU cc7865e3, reimplemented.
     */
    FurnitureMovementError furnitureFitsAt(
            RoomTile tile, UserItem item, int rotation, boolean checkForUnits, User actor) {
        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation)) {
            return FurnitureMovementError.INVALID_MOVE;
        }
        if (this.isStackPlacementBypassItem(item)) {
            return FurnitureMovementError.NONE;
        }

        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        for (RoomTile occupiedTile : occupiedTiles) {
            if (occupiedTile.state == RoomTileState.INVALID) {
                return FurnitureMovementError.INVALID_MOVE;
            }
            if (!Emulator.getConfig().getBoolean("wired.place.under", false)
                    || Emulator.getConfig().getBoolean("wired.place.under", false)
                            && !item.isWalkable()
                            && !item.getBaseItem().allowSit()
                            && !item.getBaseItem().allowLay()) {
                if (checkForUnits && hasOtherUsersAt(this.room, occupiedTile.x, occupiedTile.y, actor)) {
                    return FurnitureMovementError.TILE_HAS_USERS;
                }
                if (checkForUnits && this.room.hasBotsAt(occupiedTile.x, occupiedTile.y)) {
                    return FurnitureMovementError.TILE_HAS_BOTS;
                }
                if (checkForUnits && this.room.hasPetsAt(occupiedTile.x, occupiedTile.y)) {
                    return FurnitureMovementError.TILE_HAS_PETS;
                }
            }
        }

        List<Pair<RoomTile, Set<UserItem>>> tileFurniture = new ArrayList<>();
        for (RoomTile occupiedTile : occupiedTiles) {
            tileFurniture.add(Pair.create(occupiedTile, this.facade.getItemsAt(occupiedTile)));
            UserItem topItem = this.facade.getTopItemAt(occupiedTile.x, occupiedTile.y, item);
            if (topItem != null && !topItem.getBaseItem().allowStack() && !occupiedTile.getAllowStack()) {
                return FurnitureMovementError.CANT_STACK;
            }
        }

        return item.canStackAt(this.room, tileFurniture)
                ? FurnitureMovementError.NONE
                : FurnitureMovementError.CANT_STACK;
    }

    FurnitureMovementError placeFloorFurniture(UserItem item, RoomTile tile, int rotation, User owner) {
        // Habbo's own floor furni cap, before anything else is asked: a new wired box is refused at it like any
        // other floor furni, whichever way it comes in (inventory, Builders Club, a shared setup, Place Furni).
        // A room already over the cap keeps its furni.
        if (!RoomFloorItemLimit.hasRoomFor(this.room.getFloorItems().size())) {
            return FurnitureMovementError.MAX_ITEMS;
        }

        RoomSpecialTypes specialTypes = this.room.getRoomSpecialTypes();
        if (specialTypes != null) {
            if (item instanceof WiredEffectSendSignal && specialTypes.isSignalSenderLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_SENDERS;
            }
            if (item instanceof WiredTriggerReceiveSignal && specialTypes.isSignalReceiverLimitReached()) {
                return FurnitureMovementError.MAX_SIGNAL_RECEIVERS;
            }
            // A variable box that arrives already permanent takes a permanent slot as it lands: with none free it
            // stays where it came from, settings and all, rather than being placed and losing its permanence.
            if (item instanceof InteractionWiredExtra extra
                    && !WiredRoomStats.hasPermanentSlotFor(specialTypes.getExtras(), extra)) {
                return FurnitureMovementError.MAX_PERMANENT_VARIABLES;
            }
        }

        boolean pluginHelper = false;
        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            FurniturePlacedEvent event =
                    Emulator.getPluginManager().fireEvent(new FurniturePlacedEvent(item, owner, tile));
            if (event.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
            pluginHelper = event.hasPluginHelper();
        }

        RoomLayout layout = this.room.getLayout();
        Set<RoomTile> occupiedTiles = layout.getTilesAt(
                tile, item.getBaseItem().getWidth(), item.getBaseItem().getLength(), rotation);
        FurnitureMovementError fits = this.furnitureFitsAt(tile, item, rotation, true, owner);
        if (fits != FurnitureMovementError.NONE && !pluginHelper) {
            return fits;
        }

        double height = tile.getStackHeight();
        for (RoomTile occupiedTile : occupiedTiles) {
            height = Math.max(height, occupiedTile.getStackHeight());
        }

        height = RoomBuildHeight.apply(owner, layout, tile, height);

        if (Emulator.getPluginManager().isRegistered(FurnitureBuildheightEvent.class, true)) {
            FurnitureBuildheightEvent event =
                    Emulator.getPluginManager().fireEvent(new FurnitureBuildheightEvent(item, owner, 0.00, height));
            if (event.hasChangedHeight()) {
                height = layout.getHeightAtSquare(tile.x, tile.y) + event.getUpdatedHeight();
            }
        }

        item.setZ(height);
        item.setX(tile.x);
        item.setY(tile.y);
        item.setRotation(rotation);
        // Placing always applies the actor's build-underpass mode, so re-placing
        // an item without the mode clears a previously set flag.
        if (owner != null && owner.getRoomUnit() != null) {
            item.setAllowUnderpass(owner.getRoomUnit().isBuildUnderpass());
        }
        this.ensureOwnerName(item, owner);
        item.needsUpdate(true);
        this.facade.addUserItem(item);
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        this.room.updateTiles(occupiedTiles);
        this.room.sendComposer(
                new AddFloorItemComposer(item, this.facade.getFurniOwnerName(item.getUserId())).compose());

        if (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item)) {
            RoomConfInvisSupport.sendState(this.room);
        }
        if (RoomAreaHideSupport.isControllerItem(item)) {
            RoomAreaHideSupport.sendState(this.room, item);
        }
        if (RoomHanditemBlockSupport.isControllerItem(item)) {
            RoomHanditemBlockSupport.sendState(this.room);
        }
        if (RoomChooserDisableSupport.isActiveController(item)) {
            RoomHanditemBlockSupport.broadcastConfigurationItemStates(this.room);
        }

        for (RoomTile occupiedTile : occupiedTiles) {
            this.room.updateUsersAt(occupiedTile.x, occupiedTile.y);
            this.room.updateBotsAt(occupiedTile.x, occupiedTile.y);
        }
        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    FurnitureMovementError placeWallFurniture(UserItem item, String wallPosition, User owner) {
        if (!(this.room.hasRights(owner)
                || this.room.getGuildRightLevel(owner).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS)
                || BuildersClubRoomSupport.canPlaceInRoom(owner, this.room))) {
            return FurnitureMovementError.NO_RIGHTS;
        }

        // Habbo's own wall furni cap; a room already over it keeps its furni.
        if (!RoomWallItemLimit.hasRoomFor(this.room.getWallItems().size())) {
            return FurnitureMovementError.MAX_ITEMS;
        }

        if (Emulator.getPluginManager().isRegistered(FurniturePlacedEvent.class, true)) {
            Event furniturePlacedEvent = new FurniturePlacedEvent(item, owner, null);
            Emulator.getPluginManager().fireEvent(furniturePlacedEvent);
            if (furniturePlacedEvent.isCancelled()) {
                return FurnitureMovementError.CANCEL_PLUGIN_PLACE;
            }
        }

        item.setWallPosition(wallPosition);
        this.ensureOwnerName(item, owner);
        item.needsUpdate(true);
        // Added before it is announced, so a Builders Club furni already has its band id.
        this.facade.addUserItem(item);
        this.room.sendComposer(
                new AddWallItemComposer(item, this.facade.getFurniOwnerName(item.getUserId())).compose());
        item.setRoomId(this.room.getId());
        item.onPlace(this.room);
        Emulator.getThreading().run(item);
        return FurnitureMovementError.NONE;
    }

    boolean isStackPlacementBypassItem(UserItem item) {
        return item instanceof InteractionStackHelper
                || item instanceof InteractionTileWalkMagic
                || item instanceof InteractionStackWalkHelper;
    }

    boolean shouldPinStackHelperToFloor(UserItem item) {
        return item instanceof InteractionStackHelper || item instanceof InteractionTileWalkMagic;
    }

    double resolveStackWalkHelperHeight(UserItem item, RoomTile tile, Set<RoomTile> occupiedTiles) {
        UserItem helper = this.findStackHeightHelperAt(tile, item);
        if (helper != null) {
            return Math.max(helper.getZ(), this.getMinimumTileHeight(occupiedTiles));
        }

        // Malformed helper data keeps the default height.
        double height = StackHelperExtradata.height(item.getExtradata());
        return Math.max(height, this.getMinimumTileHeight(occupiedTiles));
    }

    UserItem findStackHeightHelperAt(RoomTile tile, UserItem exclude) {
        if (tile == null) {
            return null;
        }
        for (UserItem helper : this.facade.getItemsAt(tile)) {
            if (helper != exclude && this.isStackPlacementBypassItem(helper)) {
                return helper;
            }
        }
        return null;
    }

    private double getMinimumTileHeight(Set<RoomTile> occupiedTiles) {
        double minimumHeight = 0.0D;
        for (RoomTile occupiedTile : occupiedTiles) {
            minimumHeight =
                    Math.max(minimumHeight, this.room.getLayout().getHeightAtSquare(occupiedTile.x, occupiedTile.y));
        }
        return minimumHeight;
    }

    private void ensureOwnerName(UserItem item, User owner) {
        if (!this.index.ownerNames().containsKey(item.getUserId()) && owner != null) {
            this.index
                    .ownerNames()
                    .put(
                            item.getUserId(),
                            item.getUserId() == BuildersClubRoomSupport.VIRTUAL_OWNER_ID
                                    ? BuildersClubRoomSupport.DISPLAY_OWNER_NAME
                                    : owner.getUserInfo().getUsername());
        }
    }

    /** Whether an avatar other than {@code actor} stands on the tile. */
    static boolean hasOtherUsersAt(Room room, int x, int y, User actor) {
        if (actor == null || actor.getRoomUnit() == null) {
            return room.hasUsersAt(x, y);
        }
        for (User user : room.getUsers()) {
            if (user == null || user == actor || user.getRoomUnit() == null) continue;
            if (user.getRoomUnit().getX() == x && user.getRoomUnit().getY() == y) return true;
        }
        return false;
    }
}
