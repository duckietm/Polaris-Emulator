package polaris.messages.outgoing.trading;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TradeClosedComposer extends MessageComposer {
    public static final int USER_CANCEL_TRADE = 0;
    public static final int ITEMS_NOT_FOUND = 1;

    private final int userId;
    private final int errorCode;

    public TradeClosedComposer(int userId, int errorCode) {
        this.userId = userId;
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TradeStoppedComposer);
        this.response.appendInt(this.userId);
        this.response.appendInt(this.errorCode);
        return this.response;
    }

    public int getUserId() {
        return userId;
    }

    public int getErrorCode() {
        return errorCode;
    }
}
