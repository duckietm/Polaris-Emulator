package com.eu.habbo.messages.incoming.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomVisitorQueueSupport;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.RoomQueueStatusMessage;

/**
 * AIR 13 {@code ChangeQueue} (3093): the {@code room_queue} widget switches
 * between the visitor queue (target 2) and the spectator queue (target 1).
 */
public class ChangeQueueEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int target = this.packet.readInt();

        if (target != RoomQueueStatusMessage.TARGET_SPECTATOR && target != RoomQueueStatusMessage.TARGET_VISITOR) {
            return;
        }

        int roomId = this.client.getHabbo().getHabboInfo().getRoomQueueId();
        if (roomId == 0) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null) {
            this.client.getHabbo().getHabboInfo().setRoomQueueId(0);
            return;
        }

        RoomVisitorQueueSupport.changeQueue(room, this.client.getHabbo(), target);
    }
}
