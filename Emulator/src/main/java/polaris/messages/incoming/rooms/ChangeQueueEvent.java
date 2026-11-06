package polaris.messages.incoming.rooms;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomVisitorQueueSupport;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomQueueStatusMessage;

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

        int roomId = this.client.getUser().getUserInfo().getRoomQueueId();
        if (roomId == 0) {
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().getRoom(roomId);
        if (room == null) {
            this.client.getUser().getUserInfo().setRoomQueueId(0);
            return;
        }

        RoomVisitorQueueSupport.changeQueue(room, this.client.getUser(), target);
    }
}
