package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomDataComposer;

public class RequestRoomDataEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(this.packet.readInt());

        int something = this.packet.readInt();
        int something2 = this.packet.readInt();
        if (room != null) {
            boolean unknown = something != 0 || something2 != 1;

            // Start background loading of room data to reduce perceived load time
            // This allows the room to start loading while the client is still processing the room info.
            // Only for rooms the user can walk into, so a refused entry never loads the contents.
            if (room.isPreLoaded()
                    && !room.isLoadedOrLoading()
                    && RoomManager.mayPrefetchRoomData(this.client.getUser(), room)) {
                room.startBackgroundLoad();
            }

            // this.client.getUser().getUserInfo().getCurrentRoom() != room
            this.client.sendResponse(new RoomDataComposer(room, this.client.getUser(), true, unknown));
        }
    }
}
