package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
