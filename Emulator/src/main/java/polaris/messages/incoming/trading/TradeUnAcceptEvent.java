package polaris.messages.incoming.trading;

import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class TradeUnAcceptEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        RoomTrade trade = user.getUserInfo().getCurrentRoom().getActiveTradeForUser(user);

        if (trade == null) return;

        trade.accept(user, false);
    }
}
