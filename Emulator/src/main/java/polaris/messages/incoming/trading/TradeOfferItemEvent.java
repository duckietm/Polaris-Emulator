package polaris.messages.incoming.trading;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class TradeOfferItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        RoomTrade trade =
                this.client.getUser().getUserInfo().getCurrentRoom().getActiveTradeForUser(this.client.getUser());

        if (trade == null) return;

        UserItem item = this.client
                .getUser()
                .getInventory()
                .getItemsComponent()
                .getUserItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

        // A rented item is only on loan: it expires back to the hotel, so trading it
        // away sells something the renter does not own.
        if (item == null || !item.getBaseItem().allowTrade() || item.hasRentPeriod()) return;

        trade.offerItem(this.client.getUser(), item);
    }
}
