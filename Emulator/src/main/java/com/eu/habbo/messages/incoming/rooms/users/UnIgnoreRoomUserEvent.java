package com.eu.habbo.messages.incoming.rooms.users;

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

        // A mute is not an ignore (it never reaches the ignore list), so unignoring a muted user
        // only ends the ignore and leaves the mute in place.
        self.getHabboStats().unignoreUser(userId);
        this.client.sendResponse(new RoomUserIgnoredComposer(userId, RoomUserIgnoredComposer.UNIGNORED));
    }
}
