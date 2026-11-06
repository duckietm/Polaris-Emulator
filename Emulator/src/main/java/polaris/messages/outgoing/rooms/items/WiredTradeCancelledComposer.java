package polaris.messages.outgoing.rooms.items;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The negotiation ended without settling. Wire layout: {@code int failureId}.
 *
 * <p>Zero means the player walked away themselves and the client stays silent about it; every other
 * id names a reason worth showing, which is why a cancellation carries one at all.
 */
public class WiredTradeCancelledComposer extends MessageComposer {
    private final int failureId;

    public WiredTradeCancelledComposer(int failureId) {
        this.failureId = failureId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeCancelledComposer);
        this.response.appendInt(this.failureId);
        return this.response;
    }
}
