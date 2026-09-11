package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomPaintComposer;

public class RoomRemoveBackgroundEvent extends MessageHandler {
    private static final String DEFAULT_BACKGROUND = "0.0";

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.currentRoom();

        if (room == null)
            return;

        if (room.getOwnerId() == this.client.getHabbo().getHabboInfo().getId() || room.hasRights(this.client.getHabbo()) || this.client.getHabbo().hasPermission(Permission.ACC_PLACEFURNI)) {
            if (DEFAULT_BACKGROUND.equals(room.getBackgroundPaint()))
                return;

            room.setBackgroundPaint(DEFAULT_BACKGROUND);
            room.setNeedsUpdate(true);
            room.sendComposer(new RoomPaintComposer("landscape", DEFAULT_BACKGROUND).compose());
        }
    }
}
