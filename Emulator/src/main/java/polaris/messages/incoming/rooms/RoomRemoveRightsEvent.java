package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class RoomRemoveRightsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        final int roomId = this.packet.readInt();

        final Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null) return;

        room.removeRights(this.client.getUser().getUserInfo().getId());
    }
}
