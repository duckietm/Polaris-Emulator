package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
