package polaris.messages.incoming.rooms.items.rentablespace;

import polaris.hotel.items.interactions.InteractionRentableSpace;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;

/** RentableSpaceCancelRent (1667): the "Cancel rental space (No refund)" button of the widget. */
public class RentSpaceCancelEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (!(item instanceof InteractionRentableSpace space)) return;

        if (!space.canCancelRent(this.client.getUser(), room)) return;

        space.endRent();
        room.updateItem(space);
        space.sendRentWidget(this.client.getUser());
    }
}
