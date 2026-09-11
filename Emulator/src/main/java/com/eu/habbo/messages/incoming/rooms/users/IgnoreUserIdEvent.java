package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/**
 * Ignores a user by id instead of by name. The client sends it right after every call for help
 * (except the topics that keep reporter and reported in touch), so the reported user stops being
 * heard even when they already left the room; the by-name handler next door covers the in-room menu.
 */
public class IgnoreUserIdEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        Habbo self = this.client.getHabbo();

        if (userId <= 0 || self == null || userId == self.getHabboInfo().getId()) return;

        if (!self.getHabboStats().ignoreUser(this.client, userId)) return;

        Room room = this.currentRoom();

        if (room == null) return;

        Habbo target = room.getHabbo(userId);

        if (target != null) {
            this.client.sendResponse(new RoomUserIgnoredComposer(target, RoomUserIgnoredComposer.IGNORED));
        }
    }
}
