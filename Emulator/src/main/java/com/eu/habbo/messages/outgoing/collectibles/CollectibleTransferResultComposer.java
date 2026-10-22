package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 transfer result (2595): 0 ok, otherwise the error code the client shows. */
public class CollectibleTransferResultComposer extends MessageComposer {
    private final int result;

    public CollectibleTransferResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleTransferResultComposer);
        this.response.appendShort(this.result);
        return this.response;
    }
}
