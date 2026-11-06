package polaris.messages.incoming.trading;

import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class TradeConfirmEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null || user.getUserInfo().getCurrentRoom() == null) return;

        RoomTrade trade = user.getUserInfo().getCurrentRoom().getActiveTradeForUser(user);

        // RoomTrade.confirm checks the acceptance itself, under its lock (a user outside the trade threw here).
        if (trade == null) return;

        trade.confirm(user);
    }
}
