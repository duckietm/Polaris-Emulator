package polaris.messages.incoming.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;

/** Stops ignoring a user by id (AIR {@code IgnoredUsersManager.unignoreUser}, composer {@code ?_-02R?/?_-gt?}). */
public class UnIgnoreRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();

        User self = this.client.getUser();

        if (userId <= 0 || self == null) return;

        // A mute is not an ignore (it never reaches the ignore list), so unignoring a muted user
        // only ends the ignore and leaves the mute in place.
        self.getUserStats().unignoreUser(userId);
        this.client.sendResponse(new RoomUserIgnoredComposer(userId, RoomUserIgnoredComposer.UNIGNORED));
    }
}
