package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AlertPurchaseUnavailableComposer extends MessageComposer {
    public static final int ILLEGAL = 0;
    public static final int REQUIRES_CLUB = 1;

    private final int code;

    public AlertPurchaseUnavailableComposer(int code) {
        this.code = code;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AlertPurchaseUnavailableComposer);
        this.response.appendInt(this.code);
        return this.response;
    }

    public int getCode() {
        return code;
    }
}
