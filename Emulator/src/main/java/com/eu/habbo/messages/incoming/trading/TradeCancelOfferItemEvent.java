package com.eu.habbo.messages.incoming.trading;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;

public class TradeCancelOfferItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());

        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() == null) return;

        RoomTrade trade =
                this.client.getHabbo().getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(this.client.getHabbo());
        if (trade != null) {
            HabboItem item =
                    trade.getRoomTradeUserForHabbo(this.client.getHabbo()).getItem(itemId);

            if (!trade.getRoomTradeUserForHabbo(this.client.getHabbo()).getAccepted() && item != null) {
                trade.removeItem(this.client.getHabbo(), item);
            }
        }
    }
}
