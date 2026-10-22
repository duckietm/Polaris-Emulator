package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

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
