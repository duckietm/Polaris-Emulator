package polaris.messages.outgoing.collectibles;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 minting enabled (3059): whether relics can be minted. */
public class CollectibleMintingEnabledComposer extends MessageComposer {
    private final boolean enabled;

    public CollectibleMintingEnabledComposer(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleMintingEnabledComposer);
        this.response.appendBoolean(this.enabled);
        return this.response;
    }
}
