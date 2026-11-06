package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 transfer fee (3909): the silver a transfer of every collectible costs. */
public class CollectibleTransferFeeComposer extends MessageComposer {
    private final int fee;

    public CollectibleTransferFeeComposer(int fee) {
        this.fee = fee;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleTransferFeeComposer);
        this.response.appendInt(this.fee);
        return this.response;
    }
}
