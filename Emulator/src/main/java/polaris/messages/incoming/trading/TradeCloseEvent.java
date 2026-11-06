package polaris.messages.incoming.trading;

import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class TradeCloseEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        Room room = user.getUserInfo().getCurrentRoom();

        if (room == null) return;

        RoomTrade trade = room.getActiveTradeForUser(user);

        if (trade == null) return;

        trade.stopTrade(user);
        room.stopTrade(trade);
    }
}
