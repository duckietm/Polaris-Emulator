package polaris.messages.outgoing.trading;

import polaris.hotel.rooms.RoomTradeUser;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TradeAcceptedComposer extends MessageComposer {
    private final RoomTradeUser tradeUser;

    public TradeAcceptedComposer(RoomTradeUser tradeUser) {
        this.tradeUser = tradeUser;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TradeAcceptedComposer);
        this.response.appendInt(this.tradeUser.getUserId());
        this.response.appendInt(this.tradeUser.getAccepted());
        return this.response;
    }

    public RoomTradeUser getTradeUser() {
        return tradeUser;
    }
}
