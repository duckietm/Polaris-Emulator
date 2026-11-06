package polaris.messages.incoming.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomMutedComposer;

public class RoomMuteEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room != null) {
            if (room.isOwner(this.client.getUser())) {
                room.setMuted(!room.isMuted());
                this.client.sendResponse(new RoomMutedComposer(room));
            }
        }
    }
}
