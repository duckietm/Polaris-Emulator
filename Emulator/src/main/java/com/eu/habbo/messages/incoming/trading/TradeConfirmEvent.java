package com.eu.habbo.messages.incoming.trading;

import com.eu.habbo.habbohotel.rooms.RoomTrade;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class TradeConfirmEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null || habbo.getHabboInfo().getCurrentRoom() == null) return;

        RoomTrade trade = habbo.getHabboInfo().getCurrentRoom().getActiveTradeForHabbo(habbo);

        // RoomTrade.confirm checks the acceptance itself, under its lock (a user outside the trade threw here).
        if (trade == null) return;

        trade.confirm(habbo);
    }
}
