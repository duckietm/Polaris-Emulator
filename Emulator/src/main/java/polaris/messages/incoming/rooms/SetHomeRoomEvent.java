package polaris.messages.incoming.rooms;

import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.users.UserHomeRoomComposer;

public class SetHomeRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomId = this.packet.readInt();

        if (roomId != this.client.getUser().getUserInfo().getHomeRoom()) {
            this.client.getUser().getUserInfo().setHomeRoom(roomId);
            this.client.sendResponse(
                    new UserHomeRoomComposer(this.client.getUser().getUserInfo().getHomeRoom(), 0));
        }
    }
}
