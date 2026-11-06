package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 {@code class_3167} (2692): reward box result, 0 ok, 1 failed, 2 wrong wallet. */
public class CollectibleRewardBoxResultComposer extends MessageComposer {
    private final int result;

    public CollectibleRewardBoxResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleRewardBoxResultComposer);
        this.response.appendShort(this.result);
        return this.response;
    }
}
