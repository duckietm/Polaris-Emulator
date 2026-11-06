package polaris.messages.outgoing.trading;

import polaris.hotel.rooms.RoomTrade;
import polaris.hotel.rooms.RoomTradeUser;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TradeStartComposer extends MessageComposer {
    private final RoomTrade roomTrade;
    private final int state;

    public TradeStartComposer(RoomTrade roomTrade) {
        this.roomTrade = roomTrade;
        this.state = 1;
    }

    public TradeStartComposer(RoomTrade roomTrade, int state) {
        this.roomTrade = roomTrade;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TradeStartComposer);
        for (RoomTradeUser tradeUser : this.roomTrade.getRoomTradeUsers()) {
            this.response.appendInt(tradeUser.getUser().getUserInfo().getId());
            this.response.appendInt(this.state);
        }
        return this.response;
    }

    public RoomTrade getRoomTrade() {
        return roomTrade;
    }

    public int getState() {
        return state;
    }
}
