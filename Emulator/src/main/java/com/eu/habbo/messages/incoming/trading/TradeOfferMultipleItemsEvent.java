package com.eu.habbo.messages.incoming.trading;

import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import gnu.trove.set.hash.THashSet;

public class TradeOfferMultipleItemsEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null)
            return;

        RoomTrade trade = this.client.getHabbo().getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(this.client.getHabbo());

        if (trade == null)
            return;

        THashSet<HabboItem> items = new THashSet<>();

        int count = this.packet.readInt();
        if (count <= 0 || count > RoomTrade.MAX_OFFERED_ITEMS)
            return;

        for (int i = 0; i < count; i++) {
            int itemId = this.packet.readInt();
            if (itemId <= 0)
                continue;

            HabboItem item = this.client.getHabbo().getInventory().getItemsComponent().getHabboItem(itemId);
            if (item != null && item.getBaseItem().allowTrade()) {
                items.add(item);
            }
        }

        trade.offerMultipleItems(this.client.getHabbo(), items);
    }
}
