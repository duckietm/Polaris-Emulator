package polaris.messages.incoming.trading;

import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class TradeCancelOfferItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        RoomTrade trade =
                this.client.getUser().getUserInfo().getCurrentRoom().getActiveTradeForUser(this.client.getUser());
        if (trade != null) {
            UserItem item = trade.getRoomTradeUserForUser(this.client.getUser()).getItem(itemId);

            if (!trade.getRoomTradeUserForUser(this.client.getUser()).getAccepted() && item != null) {
                trade.removeItem(this.client.getUser(), item);
            }
        }
    }
}
