package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 {@code class_3032} mint result (official 954, taken here: 9421): 1 when the relic was minted. */
public class CollectibleMintResultComposer extends MessageComposer {
    private final int result;

    public CollectibleMintResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleMintResultComposer);
        this.response.appendShort(this.result);
        return this.response;
    }
}
