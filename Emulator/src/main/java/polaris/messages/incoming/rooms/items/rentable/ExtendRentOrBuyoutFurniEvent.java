package polaris.messages.incoming.rooms.items.rentable;

import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;

/** ExtendRentOrBuyoutFurni (1071): extend or buy out a rented furni placed in the current room. */
public class ExtendRentOrBuyoutFurniEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        boolean isWallItem = this.packet.readBoolean();
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean buyout = this.packet.readBoolean();

        if (!RoomItemInputGuard.isPositiveId(itemId)) return;

        Room room = this.client.getUser().getUserInfo().getCurrentRoom();

        if (room == null) return;

        UserItem item = room.getUserItem(itemId);

        if (item == null) return;

        if (!RentableFurnitureManager.extendOrBuyout(this.client.getUser(), item, buyout)) return;

        room.updateItem(item);
    }
}
