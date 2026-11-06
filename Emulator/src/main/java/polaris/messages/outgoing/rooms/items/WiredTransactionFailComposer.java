package polaris.messages.outgoing.rooms.items;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * A wired transaction failed outside an open trade window (AIR {@code WiredTransactionFail}):
 * {@code int failureReason}, shown as {@code wired_transactions.notification.fail.<reason>}. Used for
 * a refused withdrawal, a reward a chest could not pay and a deposit that could not start.
 */
public class WiredTransactionFailComposer extends MessageComposer {
    private final int reason;

    public WiredTransactionFailComposer(int reason) {
        this.reason = reason;
    }

    public int getReason() {
        return this.reason;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTransactionFailComposer);
        this.response.appendInt(this.reason);
        return this.response;
    }
}
