package polaris.messages.incoming.trading;

import java.util.HashSet;
import java.util.Set;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

public class TradeOfferMultipleItemsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().getUserInfo().getCurrentRoom() == null) return;

        RoomTrade trade =
                this.client.getUser().getUserInfo().getCurrentRoom().getActiveTradeForUser(this.client.getUser());

        if (trade == null) return;

        Set<UserItem> items = new HashSet<>();

        int count = this.packet.readInt();
        if (count <= 0 || count > RoomTrade.MAX_OFFERED_ITEMS) return;

        int[] itemIds = new int[count];
        for (int i = 0; i < count; i++) {
            itemIds[i] = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        }

        if (!TradeItemIdGuard.arePositiveAndUnique(itemIds)) return;

        for (int itemId : itemIds) {
            UserItem item =
                    this.client.getUser().getInventory().getItemsComponent().getUserItem(itemId);
            // A rented item is only on loan; see TradeOfferItemEvent.
            if (item == null || !item.getBaseItem().allowTrade() || item.hasRentPeriod()) return;

            items.add(item);
        }

        if (items.size() != count) return;

        trade.offerMultipleItems(this.client.getUser(), items);
    }
}
