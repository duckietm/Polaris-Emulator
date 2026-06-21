package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomRightLevels;
import com.eu.habbo.habbohotel.rooms.RoomUnitStatus;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomRightsComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserRemoveRightsComposer;

public class RoomRemoveAllRightsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        final Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();

        if (room == null || room.getId() != this.packet.readInt())
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || this.client.getHabbo().hasPermission(Permission.ACC_ANYROOMOWNER)) {
            for (int value : room.getRights().toIntArray()) {
                Habbo habbo = room.getHabbo(value);

                if (habbo != null) {
                    room.sendComposer(new RoomUserRemoveRightsComposer(room, value).compose());
                    habbo.getRoomUnit().removeStatus(RoomUnitStatus.FLAT_CONTROL);
                    habbo.getClient().sendResponse(new RoomRightsComposer(RoomRightLevels.NONE));
                }
            }

            room.removeAllRights();
        }
    }
}
