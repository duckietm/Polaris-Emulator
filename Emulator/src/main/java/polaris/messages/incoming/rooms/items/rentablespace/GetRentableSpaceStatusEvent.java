package polaris.messages.incoming.rooms.items.rentablespace;

import polaris.hotel.items.interactions.InteractionRentableSpace;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;

/** GetRentableSpaceStatus (872): the widget asks who rents a space and what it costs. */
public class GetRentableSpaceStatusEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionRentableSpace)) return;

        ((InteractionRentableSpace) item).sendRentWidget(this.client.getUser());
    }
}
