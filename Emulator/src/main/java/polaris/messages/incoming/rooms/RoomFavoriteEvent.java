package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.FavoriteRoomChangedComposer;

public class RoomFavoriteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);

        boolean added = true;
        if (room != null) {
            if (this.client.getUser().getUserStats().hasFavoriteRoom(roomId)) {
                this.client.getUser().getUserStats().removeFavoriteRoom(roomId);
                added = false;
            } else {
                if (!this.client.getUser().getUserStats().addFavoriteRoom(roomId)) {
                    return;
                }
            }

            this.client.sendResponse(new FavoriteRoomChangedComposer(roomId, added));
        }
    }
}
