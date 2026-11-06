package polaris.messages.incoming.rooms.users;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class UnbanRoomUserEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();

        if (!RoomUserInputGuard.isPositiveId(userId) || !RoomUserInputGuard.isPositiveId(roomId)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        if (room.isOwner(this.client.getUser())) {
            room.unbanUser(userId);
        }
    }
}
