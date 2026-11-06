package polaris.plugin.events.trading;

import polaris.hotel.rooms.RoomTradeUser;
import polaris.plugin.Event;

public class TradeConfirmEvent extends Event {
    public final RoomTradeUser userOne;
    public final RoomTradeUser userTwo;

    public TradeConfirmEvent(RoomTradeUser userOne, RoomTradeUser userTwo) {
        this.userOne = userOne;
        this.userTwo = userTwo;
    }
}
