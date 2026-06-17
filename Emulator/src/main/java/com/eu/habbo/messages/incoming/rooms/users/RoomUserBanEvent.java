package com.eu.habbo.messages.incoming.rooms.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;

public class RoomUserBanEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int userId = this.packet.readInt();
        int roomId = this.packet.readInt();
        String banName = this.packet.readString();

        if (!RoomUserInputGuard.isPositiveId(userId) || !RoomUserInputGuard.isPositiveId(roomId)) {
            return;
        }

        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null || room.getId() != roomId) {
            return;
        }

        RoomManager.RoomBanTypes banType;
        try {
            banType = RoomManager.RoomBanTypes.valueOf(banName);
        } catch (IllegalArgumentException e) {
            return;
        }

        Emulator.getGameEnvironment().getRoomManager().banUserFromRoom(this.client.getHabbo(), userId, roomId, banType);
    }
}
