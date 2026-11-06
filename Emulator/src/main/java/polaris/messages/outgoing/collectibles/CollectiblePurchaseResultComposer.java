package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 {@code class_2196} (169): collectible or stamp purchase result, 0 ok and 1 error. */
public class CollectiblePurchaseResultComposer extends MessageComposer {
    private final int result;

    public CollectiblePurchaseResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectiblePurchaseResultComposer);
        this.response.appendShort(this.result);
        return this.response;
    }
}
