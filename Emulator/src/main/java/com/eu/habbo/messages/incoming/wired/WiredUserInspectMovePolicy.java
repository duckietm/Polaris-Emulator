package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUnitType;
import com.eu.habbo.habbohotel.users.Habbo;

/** Server-side authority policy for creator-tool room-unit previews and movement. */
final class WiredUserInspectMovePolicy {
    private WiredUserInspectMovePolicy() {}

    static boolean canMove(Room room, Habbo requester, RoomUnit target) {
        if (room == null || requester == null || target == null || !room.canModifyWired(requester)) {
            return false;
        }

        RoomUnitType targetType = target.getRoomUnitType();
        if (targetType == RoomUnitType.BOT || targetType == RoomUnitType.PET) {
            return true;
        }
        if (targetType != RoomUnitType.USER) {
            return false;
        }

        RoomUnit requesterUnit = requester.getRoomUnit();
        if (requesterUnit != null && requesterUnit.getId() == target.getId()) {
            return true;
        }

        int requesterId =
                requester.getHabboInfo() == null ? 0 : requester.getHabboInfo().getId();
        return requesterId > 0
                && (room.getOwnerId() == requesterId || requester.hasPermission(Permission.ACC_SUPERWIRED));
    }
}
