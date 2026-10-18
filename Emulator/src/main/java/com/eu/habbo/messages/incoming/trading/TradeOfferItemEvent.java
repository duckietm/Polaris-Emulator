package com.eu.habbo.messages.incoming.trading;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class TradeOfferItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null) return;

        RoomTrade trade =
                this.client.getHabbo().getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(this.client.getHabbo());

        if (trade == null) return;

        HabboItem item = this.client
                .getHabbo()
                .getInventory()
                .getItemsComponent()
                .getHabboItem(BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt()));

        if (item == null || !item.getBaseItem().allowTrade()) return;

        trade.offerItem(this.client.getHabbo(), item);
    }
}
