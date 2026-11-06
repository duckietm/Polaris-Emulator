package polaris.messages.incoming.rooms.users;

import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserTagsComposer;

public class RequestRoomUserTagsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int roomUnitId = this.packet.readInt();

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            User user = this.client.getUser().getUserInfo().getCurrentRoom().getUserByRoomUnitId(roomUnitId);

            if (user != null) {
                this.client.sendResponse(new RoomUserTagsComposer(user));
            }
        }
    }
}
