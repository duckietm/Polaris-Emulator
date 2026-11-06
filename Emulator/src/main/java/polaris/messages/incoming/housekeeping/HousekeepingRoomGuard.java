package polaris.messages.incoming.housekeeping;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

final class HousekeepingRoomGuard {
    private HousekeepingRoomGuard() {}

    static boolean canManageRoom(User operator, Room room) {
        return room != null && HousekeepingTargetRankGuard.canTargetUser(operator, room.getOwnerId());
    }
}
