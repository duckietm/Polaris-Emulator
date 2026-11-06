package polaris.hotel.rooms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionGate;
import polaris.hotel.items.interactions.InteractionPyramid;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.RideablePet;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import polaris.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import polaris.plugin.Event;
import polaris.plugin.events.furniture.FurnitureRolledEvent;
import polaris.plugin.events.users.UserRolledEvent;

/**
 * Manages roller mechanics within a room.
 * Handles roller cycling, moving items and units on rollers.
 */
public class RoomRollerManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomRollerManager.class);

    private final Room room;

    public RoomRollerManager(Room room) {
        this.room = room;
    }

    /**
     * Processes roller cycle for the room.
     * @param updatedUnit Set to add updated room units to
     * @param cycleTimestamp Current cycle timestamp
     * @return true if roller cycle was processed
     */
    public boolean processRollerCycle(Set<RoomUnit> updatedUnit, long cycleTimestamp) {
        // Note: cycle gating is handled by RoomCycleManager.processRollers().
        // Do not add a second gate here — it would cause rollers to fire at
        // speed^2 intervals instead of the intended speed.

        // In the order they are composed: a ridden horse before its rider, a stack from the top down.
        Set<MessageComposer> messages = new LinkedHashSet<>();
        Set<Integer> rollerFurniIds = new HashSet<>();
        Set<Integer> rolledUnitIds = new HashSet<>();

        for (InteractionRoller roller :
                this.room.getRoomSpecialTypes().rollerSnapshot().values()) {
            processRoller(roller, messages, rollerFurniIds, rolledUnitIds, updatedUnit);
        }

        // Process pyramids
        int currentTime = (int) (cycleTimestamp / 1000);
        for (UserItem pyramid : this.room.getRoomSpecialTypes().getItemsOfType(InteractionPyramid.class)) {
            if (pyramid instanceof InteractionPyramid) {
                if (((InteractionPyramid) pyramid).getNextChange() < currentTime) {
                    ((InteractionPyramid) pyramid).change(this.room);
                }
            }
        }

        return true;
    }

    /** What the tile in front of a roller lets on: units, furniture, and the roller it may chain into. */
    private record RollTarget(
            UserItem newRoller, boolean allowUsers, boolean allowFurniture, boolean stackContainsRoller) {}

    /**
     * Processes a single roller and its contents.
     */
    private void processRoller(
            InteractionRoller roller,
            Set<MessageComposer> messages,
            Set<Integer> rollerFurniIds,
            Set<Integer> rolledUnitIds,
            Set<RoomUnit> updatedUnit) {

        RoomLayout layout = this.room.getLayout();

        RoomTile rollerTile = layout.getTile(roller.getX(), roller.getY());
        if (rollerTile == null) {
            return;
        }

        Set<UserItem> itemsOnRoller = this.itemsOnRoller(roller, rollerTile);

        if (!rollerTile.hasUnits() && itemsOnRoller.isEmpty()) {
            return;
        }

        RoomTile tileInFront = this.tileInFront(layout, roller, rollerTile);

        if (!this.canRollOnto(layout, tileInFront)) {
            return;
        }

        Set<UserItem> itemsNewTile = new HashSet<>();
        itemsNewTile.addAll(this.room.getItemsAt(tileInFront));
        itemsNewTile.removeAll(itemsOnRoller);

        itemsOnRoller.removeAll(this.notOnThisRoller(roller, itemsOnRoller, rollerFurniIds));
        UserItem topItem = this.room.getTopItemAt(tileInFront.x, tileInFront.y);

        RollTarget target = this.inspectTileInFront(roller, rollerTile, tileInFront, itemsNewTile, topItem);
        UserItem newRoller = target.newRoller();
        boolean allowUsers = target.allowUsers();
        boolean allowFurniture = target.allowFurniture();
        boolean stackContainsRoller = target.stackContainsRoller();

        Double zOffset = this.zOffset(roller, rollerTile, tileInFront, newRoller, itemsNewTile);
        if (zOffset == null) {
            return;
        }

        // Process units on roller
        if (allowUsers) {
            processUnitsOnRoller(
                    roller,
                    rollerTile,
                    tileInFront,
                    topItem,
                    itemsOnRoller,
                    itemsNewTile,
                    stackContainsRoller,
                    allowFurniture,
                    zOffset,
                    messages,
                    rolledUnitIds,
                    updatedUnit);
        }

        // Send unit messages
        this.sendAll(messages);

        // Process furniture on roller
        if (allowFurniture || !stackContainsRoller || InteractionRoller.NO_RULES) {
            processFurnitureOnRoller(
                    roller, itemsOnRoller, newRoller, topItem, tileInFront, zOffset, messages, rollerFurniIds);
        }

        // Send furniture messages
        this.sendAll(messages);
    }

    /** The items standing on top of the roller: at or above its own top. */
    private Set<UserItem> itemsOnRoller(InteractionRoller roller, RoomTile rollerTile) {
        Set<UserItem> itemsOnRoller = new HashSet<>();
        for (UserItem item : this.room.getItemsAt(rollerTile)) {
            if (item.getZ() >= roller.getZ() + Item.getCurrentHeight(roller)) {
                itemsOnRoller.add(item);
            }
        }
        itemsOnRoller.remove(roller);
        return itemsOnRoller;
    }

    /** The tile the roller moves its contents to; a sloped tile is skipped in the directions that climb or fall. */
    private RoomTile tileInFront(RoomLayout layout, InteractionRoller roller, RoomTile rollerTile) {
        RoomTile tileInFront =
                layout.getTileInFront(layout.getTile(roller.getX(), roller.getY()), roller.getRotation());

        //   - Rot 2 (East) / Rot 4 (South): skip only when going uphill
        //   - Rot 0 (North) / Rot 6 (West): skip only when going downhill
        int rot = roller.getRotation();
        boolean isEastSouth = (rot == 2 || rot == 4);
        boolean isNorthWest = (rot == 0 || rot == 6);
        if ((isEastSouth || isNorthWest) && tileInFront != null && layout.tileExists(tileInFront.x, tileInFront.y)) {
            double baseHeight = layout.getHeightAtSquare(tileInFront.x, tileInFront.y);
            double floorAlt = layout.getFloorAltitude(tileInFront.x, tileInFront.y);

            if (Math.abs(floorAlt - baseHeight) > 0.1) {
                boolean hasRollerOnTile = false;
                for (UserItem item : this.room.getItemsAt(tileInFront)) {
                    if (item instanceof InteractionRoller) {
                        hasRollerOnTile = true;
                        break;
                    }
                }

                if (!hasRollerOnTile) {
                    RoomTile nextTile = layout.getTileInFront(tileInFront, roller.getRotation());
                    if (nextTile != null && layout.tileExists(nextTile.x, nextTile.y)) {
                        double rollerZ = layout.getHeightAtSquare(rollerTile.x, rollerTile.y);
                        double destZ = layout.getHeightAtSquare(nextTile.x, nextTile.y);
                        boolean goingUphill = destZ > rollerZ;
                        boolean goingDownhill = destZ < rollerZ;

                        if ((isEastSouth && goingUphill) || (isNorthWest && goingDownhill)) {
                            tileInFront = nextTile;
                        }
                    }
                }
            }
        }

        return tileInFront;
    }

    /** Whether anything can be rolled onto the tile: it exists, takes a stack or a unit, and nobody stands on it. */
    private boolean canRollOnto(RoomLayout layout, RoomTile tileInFront) {
        if (tileInFront == null || !layout.tileExists(tileInFront.x, tileInFront.y)) {
            return false;
        }

        if (tileInFront.state == RoomTileState.INVALID) {
            return false;
        }

        if (!tileInFront.getAllowStack()
                && !(tileInFront.isWalkable()
                        || tileInFront.state == RoomTileState.SIT
                        || tileInFront.state == RoomTileState.LAY)) {
            return false;
        }

        if (tileInFront.hasUnits()) {
            return false;
        }

        return true;
    }

    /** Items that are not this roller's to move: they stand on another tile too, or a roller moved them already. */
    private List<UserItem> notOnThisRoller(
            InteractionRoller roller, Set<UserItem> itemsOnRoller, Set<Integer> rollerFurniIds) {
        List<UserItem> toRemove = new ArrayList<>();
        for (UserItem item : itemsOnRoller) {
            if (item.getX() != roller.getX() || item.getY() != roller.getY() || rollerFurniIds.contains(item.getId())) {
                toRemove.add(item);
            }
        }
        return toRemove;
    }

    /** What the items already on the tile in front let on, and the roller there, if any. */
    private RollTarget inspectTileInFront(
            InteractionRoller roller,
            RoomTile rollerTile,
            RoomTile tileInFront,
            Set<UserItem> itemsNewTile,
            UserItem topItem) {
        UserItem newRoller = null;
        boolean allowUsers = true;
        boolean allowFurniture = true;
        boolean stackContainsRoller = false;

        for (UserItem item : itemsNewTile) {
            if (!(item.getBaseItem().allowWalk() || item.getBaseItem().allowSit())
                    && !(item instanceof InteractionGate && item.getExtradata().equals("1"))) {
                allowUsers = false;
            }
            if (item instanceof InteractionRoller) {
                newRoller = item;
                stackContainsRoller = true;

                double rollerRelativeZ = roller.getZ() - rollerTile.z;
                double newRollerRelativeZ = item.getZ() - tileInFront.z;
                if ((Math.abs(rollerRelativeZ - newRollerRelativeZ) > 0.1
                                || (itemsNewTile.size() > 1 && item != topItem))
                        && !InteractionRoller.NO_RULES) {
                    allowUsers = false;
                    allowFurniture = false;
                    continue;
                }

                break;
            } else {
                allowFurniture = false;
            }
        }

        if (allowFurniture) {
            allowFurniture = tileInFront.getAllowStack();
        }

        return new RollTarget(newRoller, allowUsers, allowFurniture, stackContainsRoller);
    }

    /**
     * How far the contents move up or down; null when the roller in front carries something else, which stops this
     * roller (unless roller rules are off).
     */
    private Double zOffset(
            InteractionRoller roller,
            RoomTile rollerTile,
            RoomTile tileInFront,
            UserItem newRoller,
            Set<UserItem> itemsNewTile) {
        double zOffset = 0;
        if (newRoller != null) {
            if ((!itemsNewTile.isEmpty() && (itemsNewTile.size() > 1)) && !InteractionRoller.NO_RULES) {
                return null;
            }

            double oldRollerTop = roller.getZ() + Item.getCurrentHeight(roller);
            double newRollerTop = newRoller.getZ() + Item.getCurrentHeight(newRoller);
            zOffset = newRollerTop - oldRollerTop;
        } else {
            zOffset = -Item.getCurrentHeight(roller) + tileInFront.getStackHeight() - rollerTile.z;
        }
        return zOffset;
    }

    private void sendAll(Set<MessageComposer> messages) {
        if (!messages.isEmpty()) {
            for (MessageComposer message : messages) {
                this.room.sendComposer(message.compose());
            }
            messages.clear();
        }
    }

    /**
     * Processes units (Habbos, Pets) on a roller.
     */
    private void processUnitsOnRoller(
            InteractionRoller roller,
            RoomTile rollerTile,
            RoomTile tileInFront,
            UserItem topItem,
            Set<UserItem> itemsOnRoller,
            Set<UserItem> itemsNewTile,
            boolean stackContainsRoller,
            boolean allowFurniture,
            double zOffset,
            Set<MessageComposer> messages,
            Set<Integer> rolledUnitIds,
            Set<RoomUnit> updatedUnit) {

        Event roomUserRolledEvent = null;

        if (Emulator.getPluginManager().isRegistered(UserRolledEvent.class, true)) {
            roomUserRolledEvent = new UserRolledEvent(null, null, null);
        }

        ArrayList<RoomUnit> unitsOnTile = this.unitsToRoll(rollerTile);

        double targetZ = this.calculateTargetZ(tileInFront, itemsOnRoller);

        this.room.getTallestChair(tileInFront);

        Set<Integer> usersRolledThisTile = new HashSet<>();

        for (RoomUnit unit : unitsOnTile) {
            if (rolledUnitIds.contains(unit.getId())) {
                continue;
            }

            if (usersRolledThisTile.size() >= Room.ROLLERS_MAXIMUM_ROLL_AVATARS) {
                break;
            }

            if (stackContainsRoller && !allowFurniture && !(topItem != null && topItem.isWalkable())) {
                continue;
            }

            if (unit.hasStatus(RoomUnitStatus.MOVE)) {
                continue;
            }

            // Prevent rolling if the unit is still in roller cooldown (prevents desync/bungie effect)
            if (!unit.canBeRolled()) {
                continue;
            }

            if (roomUserRolledEvent != null && unit.getRoomUnitType() == RoomUnitType.USER) {
                roomUserRolledEvent = new UserRolledEvent(this.room.getUser(unit), roller, tileInFront);
                Emulator.getPluginManager().fireEvent(roomUserRolledEvent);

                if (roomUserRolledEvent.isCancelled()) {
                    continue;
                }
            }

            double newZ = this.rollRiddenPet(unit, roller, tileInFront, targetZ, messages, rolledUnitIds, updatedUnit);

            usersRolledThisTile.add(unit.getId());
            rolledUnitIds.add(unit.getId());
            updatedUnit.remove(unit);

            messages.add(new RoomUnitOnRollerComposer(
                    unit, roller, unit.getCurrentLocation(), unit.getZ(), tileInFront, newZ, this.room));

            if (itemsOnRoller.isEmpty()) {
                this.scheduleWalkOn(unit, rollerTile, tileInFront, itemsNewTile, itemsOnRoller);
            }

            if (unit.hasStatus(RoomUnitStatus.SIT)) {
                unit.sitUpdate = true;
            }
        }
    }

    /** The units on the roller tile, without a pet that is being ridden: it moves with its rider. */
    private ArrayList<RoomUnit> unitsToRoll(RoomTile rollerTile) {
        ArrayList<RoomUnit> unitsOnTile = new ArrayList<>(rollerTile.getUnits());

        for (RoomUnit unit : rollerTile.getUnits()) {
            if (unit.getRoomUnitType() == RoomUnitType.PET) {
                Pet pet = this.room.getPet(unit);
                if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null) {
                    unitsOnTile.remove(unit);
                }
            }
        }

        return unitsOnTile;
    }

    /**
     * For a user riding a horse, rolls the horse first and returns the rider's height one above it; otherwise
     * returns the target height unchanged.
     */
    private double rollRiddenPet(
            RoomUnit unit,
            InteractionRoller roller,
            RoomTile tileInFront,
            double targetZ,
            Set<MessageComposer> messages,
            Set<Integer> rolledUnitIds,
            Set<RoomUnit> updatedUnit) {
        double newZ = targetZ;

        // Horse riding
        if (unit.getRoomUnitType() == RoomUnitType.USER) {
            User rollingUser = this.room.getUser(unit);
            if (rollingUser != null && rollingUser.getUserInfo() != null) {
                RideablePet riding = rollingUser.getUserInfo().getRiding();
                if (riding != null && riding.getRoomUnit() != null) {
                    RoomUnit ridingUnit = riding.getRoomUnit();
                    double petOldZ = ridingUnit.getZ();
                    double petNewZ = targetZ;

                    // Update pet position immediately before composing messages to prevent desync
                    rolledUnitIds.add(ridingUnit.getId());
                    updatedUnit.remove(ridingUnit);

                    // Compose and send pet roller message first
                    RoomUnitOnRollerComposer petRollerComposer = new RoomUnitOnRollerComposer(
                            ridingUnit,
                            roller,
                            ridingUnit.getCurrentLocation(),
                            petOldZ,
                            tileInFront,
                            petNewZ,
                            this.room);
                    messages.add(petRollerComposer);

                    // Update newZ for the rider (1 unit above pet)
                    newZ = petNewZ + 1.0;
                }
            }
        }

        return newZ;
    }

    /** A unit rolled off an empty roller walks onto the top item in front, once the roll has played. */
    private void scheduleWalkOn(
            RoomUnit unit,
            RoomTile rollerTile,
            RoomTile tileInFront,
            Set<UserItem> itemsNewTile,
            Set<UserItem> itemsOnRoller) {
        UserItem item = this.room.getTopItemAt(tileInFront.x, tileInFront.y);

        if (item != null && itemsNewTile.contains(item) && !itemsOnRoller.contains(item)) {
            final RoomUnit finalUnit = unit;
            final RoomTile finalRollerTile = rollerTile;
            Emulator.getThreading()
                    .run(
                            () -> {
                                if (finalUnit.getGoal() == finalRollerTile) {
                                    try {
                                        item.onWalkOn(
                                                finalUnit, this.room, new Object[] {finalRollerTile, tileInFront});
                                    } catch (Exception e) {
                                        LOGGER.error("Caught exception", e);
                                    }
                                }
                            },
                            RoomQueueSpeedControlSupport.getEffectiveRollerIntervalMs(this.room));
        }
    }

    /**
     * Processes furniture items on a roller.
     */
    private void processFurnitureOnRoller(
            InteractionRoller roller,
            Set<UserItem> itemsOnRoller,
            UserItem newRoller,
            UserItem topItem,
            RoomTile tileInFront,
            double zOffset,
            Set<MessageComposer> messages,
            Set<Integer> rollerFurniIds) {

        Event furnitureRolledEvent = null;

        if (Emulator.getPluginManager().isRegistered(FurnitureRolledEvent.class, true)) {
            furnitureRolledEvent = new FurnitureRolledEvent(null, null, null);
        }

        if (newRoller == null || topItem == newRoller) {
            List<UserItem> sortedItems = new ArrayList<>(itemsOnRoller);
            sortedItems.sort((o1, o2) -> o1.getZ() > o2.getZ() ? -1 : 1);

            for (UserItem item : sortedItems) {
                if (item.getX() == roller.getX() && item.getY() == roller.getY() && zOffset <= 0) {
                    if (item != roller) {
                        if (!this.footprintFitsInFront(item, roller, tileInFront)) {
                            continue;
                        }

                        if (furnitureRolledEvent != null) {
                            furnitureRolledEvent = new FurnitureRolledEvent(item, roller, tileInFront);
                            Emulator.getPluginManager().fireEvent(furnitureRolledEvent);

                            if (furnitureRolledEvent.isCancelled()) {
                                continue;
                            }
                        }

                        messages.add(new FloorItemOnRollerComposer(item, roller, tileInFront, zOffset, this.room));
                        rollerFurniIds.add(item.getId());
                    }
                }
            }
        }
    }

    /**
     * A furni larger than one tile rolls only when its whole footprint lands on the map, on valid tiles and on no
     * avatar: only the tile in front of the roller was checked, so a row of rollers could push a 2x2 off the map or
     * onto a hole and the position was saved.
     */
    private boolean footprintFitsInFront(UserItem item, InteractionRoller roller, RoomTile tileInFront) {
        int width = item.getBaseItem().getWidth();
        int length = item.getBaseItem().getLength();
        if (width <= 1 && length <= 1) {
            return true;
        }

        RoomLayout layout = this.room.getLayout();
        if (!layout.fitsOnMap(tileInFront, width, length, item.getRotation())) {
            return false;
        }

        Set<RoomTile> current =
                layout.getTilesAt(layout.getTile(roller.getX(), roller.getY()), width, length, item.getRotation());
        for (RoomTile tile : layout.getTilesAt(tileInFront, width, length, item.getRotation())) {
            if (tile.state == RoomTileState.INVALID) {
                return false;
            }
            if (!current.contains(tile) && tile.hasUnits()) {
                return false;
            }
        }
        return true;
    }

    private double calculateTargetZ(RoomTile targetTile, Set<UserItem> itemsBeingRolled) {
        UserItem topItem = this.room.getTopItemAt(targetTile.x, targetTile.y);

        // Ignore items that are being rolled along with the unit
        if (topItem != null && itemsBeingRolled.contains(topItem)) {
            topItem = null;
        }

        if (topItem != null) {
            if (topItem.getBaseItem().allowSit() || topItem.getBaseItem().allowLay()) {
                return topItem.getZ();
            }
            return topItem.getZ() + Item.getCurrentHeight(topItem);
        }

        return this.room.getLayout().getFloorAltitude(targetTile.x, targetTile.y);
    }
}
