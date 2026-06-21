package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;

final class HousekeepingRoomGuard {
    private HousekeepingRoomGuard() {
    }

    static boolean canManageRoom(Habbo operator, Room room) {
        return room != null && HousekeepingTargetRankGuard.canTargetUser(operator, room.getOwnerId());
    }
}
