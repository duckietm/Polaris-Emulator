package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

final class RoomPostureService {
    static final String FLOOR_SIT_STATUS = "0.55 1";

    /** What {@link #settleOnChair} did: nothing, stood the unit up, or sat it down. */
    enum ChairOutcome {
        UNCHANGED,
        STOOD_UP,
        SAT
    }

    private final Room room;

    RoomPostureService(Room room) {
        this.room = room;
    }

    void update(RoomUnit unit) {
        HabboItem item = this.room.getTopItemAt(unit.getX(), unit.getY());
        if ((item == null && !unit.cmdSit)
                || (item != null && !item.getBaseItem().allowSit())) {
            unit.removeStatus(RoomUnitStatus.SIT);
        }

        double oldZ = unit.getZ();
        if (item != null) {
            unit.setZ(item.getBaseItem().allowSit() ? item.getZ() : item.getZ() + Item.getCurrentHeight(item));
            if (oldZ != unit.getZ()) {
                this.room.scheduledTasks.add(() -> {
                    try {
                        item.onWalkOn(unit, this.room, null);
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        this.room.sendComposer(new RoomUserStatusComposer(unit).compose());
    }

    /**
     * A unit that is not walking sits on the tallest chair of a sit tile (again when it asked to with
     * {@code sitUpdate}), and stands up when there is no chair or it rides a pet. A pet hanging, swinging, burning or
     * playing does not sit.
     */
    ChairOutcome settleOnChair(RoomUnit unit, boolean riding) {
        boolean hasSpecialPetStatus = unit.hasStatus(RoomUnitStatus.HANG)
                || unit.hasStatus(RoomUnitStatus.SWING)
                || unit.hasStatus(RoomUnitStatus.FLAME)
                || unit.hasStatus(RoomUnitStatus.PLAY);

        RoomTile thisTile = this.room.getLayout().getTile(unit.getX(), unit.getY());
        HabboItem topItem = this.room.getTallestChair(thisTile);

        if (riding || topItem == null || !topItem.getBaseItem().allowSit()) {
            if (unit.hasStatus(RoomUnitStatus.SIT)) {
                unit.removeStatus(RoomUnitStatus.SIT);
                return ChairOutcome.STOOD_UP;
            }
        } else if (!hasSpecialPetStatus
                && thisTile.state == RoomTileState.SIT
                && (!unit.hasStatus(RoomUnitStatus.SIT) || unit.sitUpdate)) {
            this.room.dance(unit, DanceType.NONE);
            unit.setStatus(RoomUnitStatus.SIT, (Item.getCurrentHeight(topItem) * 1.0D) + "");
            unit.setZ(topItem.getZ());
            unit.setRotation(RoomUserRotation.values()[topItem.getRotation()]);
            unit.sitUpdate = false;
            return ChairOutcome.SAT;
        }

        return ChairOutcome.UNCHANGED;
    }

    /**
     * A unit that is not walking lays on the bed it stands on, and gets up when there is no bed or it rides a pet.
     * Returns whether its status changed.
     */
    boolean settleOnBed(RoomUnit unit, boolean riding) {
        HabboItem topItem = this.room.getTopItemAt(unit.getX(), unit.getY());

        if (riding || topItem == null || !topItem.getBaseItem().allowLay()) {
            if (unit.hasStatus(RoomUnitStatus.LAY)) {
                unit.removeStatus(RoomUnitStatus.LAY);
                return true;
            }
        } else {
            if (!unit.hasStatus(RoomUnitStatus.LAY)) {
                BedProfile.lay(this.room, topItem, unit);

                this.room.getUnitManager().checkBedLoveEffect(topItem);

                return true;
            }
        }

        return false;
    }

    void makeSit(Habbo habbo) {
        RoomUnit unit = habbo.getRoomUnit();
        if (unit == null || unit.hasStatus(RoomUnitStatus.SIT) || !unit.canForcePosture()) {
            return;
        }

        this.room.dance(habbo, DanceType.NONE);
        unit.cmdSit = true;
        this.alignBodyRotation(unit);
        // Official floor sit: height 0.55 and the "can stand up" flag (AIR reads it for the Stand button).
        // Behaviour observed in plus-emulator/PlusEMU a4ed8bbf, reimplemented.
        unit.setStatus(RoomUnitStatus.SIT, FLOOR_SIT_STATUS);
        this.room.sendComposer(new RoomUserStatusComposer(unit).compose());
        WiredManager.triggerUserPerformsAction(this.room, unit, WiredUserActionType.SIT, -1);
    }

    void makeStand(Habbo habbo) {
        RoomUnit unit = habbo.getRoomUnit();
        if (unit == null) {
            return;
        }

        HabboItem item = this.room.getTopItemAt(unit.getX(), unit.getY());
        if (item != null && item.getBaseItem().allowSit() && item.getBaseItem().allowLay()) {
            return;
        }

        boolean wasSittingOrLaying = unit.hasStatus(RoomUnitStatus.SIT) || unit.hasStatus(RoomUnitStatus.LAY);
        unit.cmdStand = true;
        this.alignBodyRotation(unit);
        unit.removeStatus(RoomUnitStatus.SIT);
        unit.removeStatus(RoomUnitStatus.LAY);
        this.room.sendComposer(new RoomUserStatusComposer(unit).compose());

        if (wasSittingOrLaying) {
            WiredManager.triggerUserPerformsAction(this.room, unit, WiredUserActionType.STAND, -1);
        }
    }

    private void alignBodyRotation(RoomUnit unit) {
        int rotation = unit.getBodyRotation().getValue();
        unit.setBodyRotation(RoomUserRotation.values()[rotation - rotation % 2]);
    }
}
