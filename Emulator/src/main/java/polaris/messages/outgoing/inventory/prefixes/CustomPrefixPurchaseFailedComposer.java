package polaris.messages.outgoing.inventory.prefixes;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CustomPrefixPurchaseFailedComposer extends MessageComposer {
    private final String message;

    public CustomPrefixPurchaseFailedComposer(String message) {
        this.message = message != null ? message : "";
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CustomPrefixPurchaseFailedComposer);
        this.response.appendString(this.message);
        return this.response;
    }
}
