package polaris.messages.incoming.rooms.users;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.messages.incoming.MessageHandler;

public class RoomUserBanEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();
        String banName = this.packet.readString();

        if (!RoomUserInputGuard.isPositiveId(userId) || !RoomUserInputGuard.isPositiveId(roomId)) {
            return;
        }

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        RoomManager.RoomBanTypes banType;
        try {
            banType = RoomManager.RoomBanTypes.valueOf(banName);
        } catch (IllegalArgumentException e) {
            return;
        }

        Emulator.getGameEnvironment().getRoomManager().banUserFromRoom(this.client.getUser(), userId, roomId, banType);
    }
}
