package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.FavoriteRoomChangedComposer;

public class RoomUnFavoriteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        if (room != null) {
            if (this.client.getUser().getUserStats().hasFavoriteRoom(roomId)) {
                this.client.getUser().getUserStats().removeFavoriteRoom(roomId);
            }

            this.client.sendResponse(new FavoriteRoomChangedComposer(roomId, false));
        }
    }
}
