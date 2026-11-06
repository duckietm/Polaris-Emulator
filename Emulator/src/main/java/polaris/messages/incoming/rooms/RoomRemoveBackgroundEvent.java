package polaris.messages.incoming.rooms;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.RoomPaintComposer;

public class RoomRemoveBackgroundEvent extends MessageHandler {
    private static final String DEFAULT_BACKGROUND = "0.0";

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        Room room = this.currentRoom();

        if (room == null) return;

        if (room.getOwnerId() == this.client.getUser().getUserInfo().getId()
                || room.hasRights(this.client.getUser())
                || this.client.getUser().hasPermission(Permission.ACC_PLACEFURNI)) {
            if (DEFAULT_BACKGROUND.equals(room.getBackgroundPaint())) return;

            room.setBackgroundPaint(DEFAULT_BACKGROUND);
            room.setNeedsUpdate(true);
            room.sendComposer(new RoomPaintComposer("landscape", DEFAULT_BACKGROUND).compose());
        }
    }
}
