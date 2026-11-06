package polaris.messages.incoming.rooms.users;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomHanditemBlockSupport;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;

public class RoomUserDropHandItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        // AIR hides the own-avatar "drop hand item" button while the room's hand item blocker is on.
        if (RoomHanditemBlockSupport.isHanditemBlocked(room)) {
            return;
        }

        this.client.getUser().getRoomUnit().setHandItem(0);
        if (room != null) {
            room.unIdle(this.client.getUser());
            room.sendComposer(new RoomUserHandItemComposer(this.client.getUser().getRoomUnit()).compose());
        }
    }
}
