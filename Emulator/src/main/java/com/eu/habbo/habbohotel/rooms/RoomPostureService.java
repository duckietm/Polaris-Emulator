package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;

final class RoomPostureService {

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

    void makeSit(Habbo habbo) {
        RoomUnit unit = habbo.getRoomUnit();
        if (unit == null || unit.hasStatus(RoomUnitStatus.SIT) || !unit.canForcePosture()) {
            return;
        }

        this.room.dance(habbo, DanceType.NONE);
        unit.cmdSit = true;
        this.alignBodyRotation(unit);
        unit.setStatus(RoomUnitStatus.SIT, "0.5");
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
