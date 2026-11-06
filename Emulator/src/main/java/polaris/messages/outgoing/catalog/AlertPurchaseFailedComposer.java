package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class AlertPurchaseFailedComposer extends MessageComposer {
    public static final int SERVER_ERROR = 0;
    public static final int ALREADY_HAVE_BADGE = 1;

    private final int error;

    public AlertPurchaseFailedComposer(int error) {
        this.error = error;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AlertPurchaseFailedComposer);
        this.response.appendInt(this.error);
        return this.response;
    }

    public int getError() {
        return error;
    }
}
