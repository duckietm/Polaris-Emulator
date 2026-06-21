package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionGate;
import com.eu.habbo.habbohotel.items.interactions.InteractionPyramid;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.RideablePet;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemOnRollerComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUnitOnRollerComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurnitureRolledEvent;
import com.eu.habbo.plugin.events.users.UserRolledEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        Set<MessageComposer> messages = new HashSet<>();
        Set<Integer> rollerFurniIds = new HashSet<>();
        Set<Integer> rolledUnitIds = new HashSet<>();

        for (InteractionRoller roller : this.room.getRoomSpecialTypes().getRollers().values()) {
            processRoller(roller, messages, rollerFurniIds, rolledUnitIds, updatedUnit);
        }

        // Process pyramids
        int currentTime = (int) (cycleTimestamp / 1000);
        for (HabboItem pyramid : this.room.getRoomSpecialTypes().getItemsOfType(InteractionPyramid.class)) {
            if (pyramid instanceof InteractionPyramid) {
                if (((InteractionPyramid) pyramid).getNextChange() < currentTime) {
                    ((InteractionPyramid) pyramid).change(this.room);
                }
            }
        }

        return true;
    }

    /**
     * Processes a single roller and its contents.
     */
    private void processRoller(InteractionRoller roller, Set<MessageComposer> messages,
                               Set<Integer> rollerFurniIds, Set<Integer> rolledUnitIds,
                               Set<RoomUnit> updatedUnit) {

        HabboItem newRoller = null;
        RoomLayout layout = this.room.getLayout();

        RoomTile rollerTile = layout.getTile(roller.getX(), roller.getY());
        if (rollerTile == null) {
            return;
        }

        Set<HabboItem> itemsOnRoller = new HashSet<>();
        for (HabboItem item : this.room.getItemsAt(rollerTile)) {
            if (item.getZ() >= roller.getZ() + Item.getCurrentHeight(roller)) {
                itemsOnRoller.add(item);
            }
        }
        itemsOnRoller.remove(roller);

        if (!rollerTile.hasUnits() && itemsOnRoller.isEmpty()) {
            return;
        }

        RoomTile tileInFront = layout.getTileInFront(layout.getTile(roller.getX(), roller.getY()), roller.getRotation());

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
                for (HabboItem item : this.room.getItemsAt(tileInFront)) {
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

        if (tileInFront == null || !layout.tileExists(tileInFront.x, tileInFront.y)) {
            return;
        }

        if (tileInFront.state == RoomTileState.INVALID) {
            return;
        }

        if (!tileInFront.getAllowStack() && !(tileInFront.isWalkable()
                || tileInFront.state == RoomTileState.SIT
                || tileInFront.state == RoomTileState.LAY)) {
            return;
        }

        if (tileInFront.hasUnits()) {
            return;
        }

        Set<HabboItem> itemsNewTile = new HashSet<>();
        itemsNewTile.addAll(this.room.getItemsAt(tileInFront));
        itemsNewTile.removeAll(itemsOnRoller);

        List<HabboItem> toRemove = new ArrayList<>();
        for (HabboItem item : itemsOnRoller) {
            if (item.getX() != roller.getX() || item.getY() != roller.getY()
                    || rollerFurniIds.contains(item.getId())) {
                toRemove.add(item);
            }
        }
        itemsOnRoller.removeAll(toRemove);
        HabboItem topItem = this.room.getTopItemAt(tileInFront.x, tileInFront.y);

        boolean allowUsers = true;
        boolean allowFurniture = true;
        boolean stackContainsRoller = false;

        for (HabboItem item : itemsNewTile) {
            if (!(item.getBaseItem().allowWalk() || item.getBaseItem().allowSit()) && !(
                    item instanceof InteractionGate && item.getExtradata().equals("1"))) {
                allowUsers = false;
            }
            if (item instanceof InteractionRoller) {
                newRoller = item;
                stackContainsRoller = true;

                double rollerRelativeZ = roller.getZ() - rollerTile.z;
                double newRollerRelativeZ = item.getZ() - tileInFront.z;
                if ((Math.abs(rollerRelativeZ - newRollerRelativeZ) > 0.1 || (itemsNewTile.size() > 1 && item != topItem))
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

        double zOffset = 0;
        if (newRoller != null) {
            if ((!itemsNewTile.isEmpty() && (itemsNewTile.size() > 1)) && !InteractionRoller.NO_RULES) {
                return;
            }

            double oldRollerTop = roller.getZ() + Item.getCurrentHeight(roller);
            double newRollerTop = newRoller.getZ() + Item.getCurrentHeight(newRoller);
            zOffset = newRollerTop - oldRollerTop;
        } else {
            zOffset = -Item.getCurrentHeight(roller) + tileInFront.getStackHeight() - rollerTile.z;
        }

        // Process units on roller
        if (allowUsers) {
            processUnitsOnRoller(roller, rollerTile, tileInFront, topItem,
                    itemsOnRoller, itemsNewTile, stackContainsRoller, allowFurniture,
                    zOffset, messages, rolledUnitIds, updatedUnit);
        }

        // Send unit messages
        if (!messages.isEmpty()) {
            for (MessageComposer message : messages) {
                this.room.sendComposer(message.compose());
            }
            messages.clear();
        }

        // Process furniture on roller
        if (allowFurniture || !stackContainsRoller || InteractionRoller.NO_RULES) {
            processFurnitureOnRoller(roller, itemsOnRoller, newRoller, topItem,
                    tileInFront, zOffset, messages, rollerFurniIds);
        }

        // Send furniture messages
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
    private void processUnitsOnRoller(InteractionRoller roller, RoomTile rollerTile,
                                      RoomTile tileInFront, HabboItem topItem,
                                      Set<HabboItem> itemsOnRoller,
                                      Set<HabboItem> itemsNewTile,
                                      boolean stackContainsRoller, boolean allowFurniture,
                                      double zOffset, Set<MessageComposer> messages,
                                      Set<Integer> rolledUnitIds, Set<RoomUnit> updatedUnit) {

        Event roomUserRolledEvent = null;

        if (Emulator.getPluginManager().isRegistered(UserRolledEvent.class, true)) {
            roomUserRolledEvent = new UserRolledEvent(null, null, null);
        }

        ArrayList<RoomUnit> unitsOnTile = new ArrayList<>(rollerTile.getUnits());

        for (RoomUnit unit : rollerTile.getUnits()) {
            if (unit.getRoomUnitType() == RoomUnitType.PET) {
                Pet pet = this.room.getPet(unit);
                if (pet instanceof RideablePet && ((RideablePet) pet).getRider() != null) {
                    unitsOnTile.remove(unit);
                }
            }
        }

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

            double newZ = targetZ;

            if (roomUserRolledEvent != null && unit.getRoomUnitType() == RoomUnitType.USER) {
                roomUserRolledEvent = new UserRolledEvent(this.room.getHabbo(unit), roller, tileInFront);
                Emulator.getPluginManager().fireEvent(roomUserRolledEvent);

                if (roomUserRolledEvent.isCancelled()) {
                    continue;
                }
            }

            // Horse riding
            boolean isRiding = false;
            if (unit.getRoomUnitType() == RoomUnitType.USER) {
                Habbo rollingHabbo = this.room.getHabbo(unit);
                if (rollingHabbo != null && rollingHabbo.getHabboInfo() != null) {
                    RideablePet riding = rollingHabbo.getHabboInfo().getRiding();
                    if (riding != null && riding.getRoomUnit() != null) {
                        RoomUnit ridingUnit = riding.getRoomUnit();
                        double petOldZ = ridingUnit.getZ();
                        double petNewZ = targetZ;

                        // Update pet position immediately before composing messages to prevent desync
                        rolledUnitIds.add(ridingUnit.getId());
                        updatedUnit.remove(ridingUnit);

                        // Compose and send pet roller message first
                        RoomUnitOnRollerComposer petRollerComposer = new RoomUnitOnRollerComposer(
                                ridingUnit, roller, ridingUnit.getCurrentLocation(), petOldZ,
                                tileInFront, petNewZ, this.room);
                        messages.add(petRollerComposer);

                        // Update newZ for the rider (1 unit above pet)
                        newZ = petNewZ + 1.0;
                        isRiding = true;
                    }
                }
            }

            usersRolledThisTile.add(unit.getId());
            rolledUnitIds.add(unit.getId());
            updatedUnit.remove(unit);

            messages.add(new RoomUnitOnRollerComposer(unit, roller, unit.getCurrentLocation(),
                    unit.getZ(), tileInFront, newZ, this.room));

            if (itemsOnRoller.isEmpty()) {
                HabboItem item = this.room.getTopItemAt(tileInFront.x, tileInFront.y);

                if (item != null && itemsNewTile.contains(item) && !itemsOnRoller.contains(item)) {
                    final RoomUnit finalUnit = unit;
                    final RoomTile finalRollerTile = rollerTile;
                    Emulator.getThreading().run(() -> {
                        if (finalUnit.getGoal() == finalRollerTile) {
                            try {
                                item.onWalkOn(finalUnit, this.room, new Object[]{finalRollerTile, tileInFront});
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                            }
                        }
                    }, RoomQueueSpeedControlSupport.getEffectiveRollerIntervalMs(this.room));
                }
            }

            if (unit.hasStatus(RoomUnitStatus.SIT)) {
                unit.sitUpdate = true;
            }
        }
    }

    /**
     * Processes furniture items on a roller.
     */
    private void processFurnitureOnRoller(InteractionRoller roller, Set<HabboItem> itemsOnRoller,
                                          HabboItem newRoller, HabboItem topItem, RoomTile tileInFront,
                                          double zOffset, Set<MessageComposer> messages,
                                          Set<Integer> rollerFurniIds) {

        Event furnitureRolledEvent = null;

        if (Emulator.getPluginManager().isRegistered(FurnitureRolledEvent.class, true)) {
            furnitureRolledEvent = new FurnitureRolledEvent(null, null, null);
        }

        if (newRoller == null || topItem == newRoller) {
            List<HabboItem> sortedItems = new ArrayList<>(itemsOnRoller);
            sortedItems.sort((o1, o2) -> o1.getZ() > o2.getZ() ? -1 : 1);

            for (HabboItem item : sortedItems) {
                if (item.getX() == roller.getX() && item.getY() == roller.getY() && zOffset <= 0) {
                    if (item != roller) {
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

    private double calculateTargetZ(RoomTile targetTile, Set<HabboItem> itemsBeingRolled) {
        HabboItem topItem = this.room.getTopItemAt(targetTile.x, targetTile.y);

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
