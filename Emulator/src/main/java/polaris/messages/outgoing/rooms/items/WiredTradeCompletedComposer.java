package polaris.messages.outgoing.rooms.items;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The negotiation settled. Carries nothing: what changed hands has already been pushed through the
 * inventory and currency updates the client acts on anyway.
 */
public class WiredTradeCompletedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeCompletedComposer);
        return this.response;
    }
}
