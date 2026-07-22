package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserHabbiconComposer;

public class RoomUserHabbiconEvent extends MessageHandler {
    private static final int MAX_HABBICON_ID = 1000000;

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getRoomUnit() == null)
            return;

        Room room = habbo.getHabboInfo().getCurrentRoom();
        if (room == null)
            return;

        int habbiconId = this.packet.readInt();
        if (habbiconId <= 0 || habbiconId > MAX_HABBICON_ID)
            return;

        room.sendComposer(new RoomUserHabbiconComposer(habbo.getRoomUnit(), habbiconId).compose());
    }
}
