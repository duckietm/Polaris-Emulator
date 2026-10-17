package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;

/**
 * The room a moderator tool request is about. A room nobody is in is not among the active rooms, yet the room tool, the room
 * chatlog and the issue chatlog must still answer for it (a moderator opens one from the room visits list or from a ticket), so
 * the room is loaded from the database when it is not active. Its contents are not loaded: the tools only need its data.
 */
final class ModToolRoomLookup {
    private ModToolRoomLookup() {}

    static Room find(RoomManager roomManager, int roomId) {
        if (roomManager == null || !ModToolTicketGuard.isPositiveId(roomId)) {
            return null;
        }

        Room room = roomManager.getRoom(roomId);

        return room != null ? room : roomManager.loadRoom(roomId);
    }
}
