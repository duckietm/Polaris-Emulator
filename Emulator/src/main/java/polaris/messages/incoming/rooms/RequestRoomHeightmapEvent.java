package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomHeightMapComposer;
import polaris.messages.outgoing.rooms.RoomRelativeMapComposer;

public class RequestRoomHeightmapEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getLoadingRoom() > 0) {
            Room room = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .loadRoom(this.client.getUser().getUserInfo().getLoadingRoom());

            if (room != null) {
                // If room is loading in background, wait for it to complete
                if (room.isLoadingInProgress()) {
                    room.waitForLoad();
                }

                if (room.getLayout() != null) {
                    this.client.sendResponse(new RoomRelativeMapComposer(room));

                    this.client.sendResponse(new RoomHeightMapComposer(room));

                    Emulator.getGameEnvironment().getRoomManager().enterRoom(this.client.getUser(), room);
                }
            }
        }
    }
}
