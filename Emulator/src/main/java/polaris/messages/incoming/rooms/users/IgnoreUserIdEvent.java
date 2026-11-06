package polaris.messages.incoming.rooms.users;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/**
 * Ignores a user by id instead of by name. The client sends it right after every call for help
 * (except the topics that keep reporter and reported in touch), so the reported user stops being
 * heard even when they already left the room; the by-name handler next door covers the in-room menu.
 */
public class IgnoreUserIdEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        User self = this.client.getUser();

        if (userId <= 0 || self == null || userId == self.getUserInfo().getId()) return;

        if (!self.getUserStats().ignoreUser(this.client, userId)) return;

        Room room = this.currentRoom();

        if (room == null) return;

        User target = room.getUser(userId);

        if (target != null) {
            this.client.sendResponse(new RoomUserIgnoredComposer(target, RoomUserIgnoredComposer.IGNORED));
        }
    }
}
