package polaris.messages.incoming.rooms;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomRightsListComposer;

public class RequestRoomRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int requestedRoomId = this.packet.readInt();
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null || room.getId() != requestedRoomId) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || this.client.getUser().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            this.client.sendResponse(new RoomRightsListComposer(room));
        }
    }
}
