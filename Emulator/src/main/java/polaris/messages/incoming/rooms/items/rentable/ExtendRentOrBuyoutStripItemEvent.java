package polaris.messages.incoming.rooms.items.rentable;

import polaris.hotel.items.rentable.RentableFurnitureManager;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.incoming.rooms.items.RoomItemInputGuard;
import polaris.messages.outgoing.inventory.InventoryUpdateItemComposer;

/** ExtendRentOrBuyoutStripItem (2115): extend or buy out a rented furni still in the inventory. */
public class ExtendRentOrBuyoutStripItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int stripId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        boolean buyout = this.packet.readBoolean();

        if (!RoomItemInputGuard.isPositiveId(stripId)) return;

        UserItem item = this.client.getUser().getInventory().getItemsComponent().getUserItem(stripId);

        if (item == null) return;

        if (!RentableFurnitureManager.extendOrBuyout(this.client.getUser(), item, buyout)) return;

        this.client.sendResponse(new InventoryUpdateItemComposer(item));
    }
}
