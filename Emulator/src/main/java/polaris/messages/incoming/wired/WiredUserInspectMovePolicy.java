package polaris.messages.incoming.wired;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUnitType;
import polaris.hotel.users.User;

/** Server-side authority policy for creator-tool room-unit previews and movement. */
final class WiredUserInspectMovePolicy {
    private WiredUserInspectMovePolicy() {}

    static boolean canMove(Room room, User requester, RoomUnit target) {
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
                requester.getUserInfo() == null ? 0 : requester.getUserInfo().getId();
        return requesterId > 0
                && (room.getOwnerId() == requesterId || requester.hasPermission(Permission.ACC_SUPERWIRED));
    }
}
