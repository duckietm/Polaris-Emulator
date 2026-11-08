package polaris.messages.incoming.rooms;

import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;

/** A user gives up the rights they hold in the room they are in. */
public class RoomRemoveRightsEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        final int roomId = this.packet.readInt();
        final int userId = this.client.getUser().getUserInfo().getId();
        final Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (!canGiveUpRights(room, roomId, userId)) return;

        room.removeRights(userId);
    }

    /** Only a user in that room, holding rights there, and not its owner, can give them up. */
    static boolean canGiveUpRights(Room room, int roomId, int userId) {
        return room != null
                && room.getId() == roomId
                && room.getOwnerId() != userId
                && room.getRightsManager().getRights().contains(userId);
    }
}
