package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

public class RequestHeightmapEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getLoadingRoom() > 0) {
            Room room = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoom(this.client.getUser().getUserInfo().getLoadingRoom());

            if (room != null) {
                // If room is loading in background, wait for it to complete
                if (room.isLoadingInProgress()) {
                    room.waitForLoad();
                }

                Emulator.getGameEnvironment().getRoomManager().enterRoom(this.client.getUser(), room);
            }
        }
    }
}
