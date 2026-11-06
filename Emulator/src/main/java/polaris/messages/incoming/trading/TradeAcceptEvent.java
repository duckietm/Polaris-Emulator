package polaris.messages.incoming.trading;

import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class TradeAcceptEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        if (user == null || user.getUserInfo() == null || user.getUserInfo().getCurrentRoom() == null) return;

        RoomTrade trade = user.getUserInfo().getCurrentRoom().getActiveTradeForUser(user);

        if (trade == null) return;

        trade.accept(user, true);
    }
}
