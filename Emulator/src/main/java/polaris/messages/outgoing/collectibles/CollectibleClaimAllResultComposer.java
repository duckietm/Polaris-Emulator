package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 claim-all result (3720): 0 when the level rewards were claimed. */
public class CollectibleClaimAllResultComposer extends MessageComposer {
    private final int result;

    public CollectibleClaimAllResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleClaimAllResultComposer);
        this.response.appendShort(this.result);
        return this.response;
    }
}
