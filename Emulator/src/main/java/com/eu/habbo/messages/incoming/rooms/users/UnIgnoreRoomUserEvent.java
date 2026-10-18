package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/** Stops ignoring a user by id (AIR {@code IgnoredUsersManager.unignoreUser}, composer {@code ?_-02R?/?_-gt?}). */
public class UnIgnoreRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        Habbo self = this.client.getHabbo();

        if (userId <= 0 || self == null) return;

        Room room = this.currentRoom();
        Habbo target = room != null ? room.getHabbo(userId) : null;

        // A user muted in this room stays silent; the mute is not lifted through the ignore list.
        if (target != null && !target.getHabboStats().allowTalk()) return;

        self.getHabboStats().unignoreUser(userId);
        this.client.sendResponse(new RoomUserIgnoredComposer(userId, RoomUserIgnoredComposer.UNIGNORED));
    }
}
