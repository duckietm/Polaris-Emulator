package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomRightsListComposer;

public class RequestRoomRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int requestedRoomId = this.packet.readInt();
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || room.getId() != requestedRoomId)
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            this.client.sendResponse(new RoomRightsListComposer(room));
        }
    }
}
