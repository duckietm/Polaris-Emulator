package polaris.messages.outgoing.catalog;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class TargetedOfferNotFoundComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TargetedOfferNotFoundComposer);
        // Empty body
        return this.response;
    }
}
