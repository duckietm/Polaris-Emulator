package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * WIRED Link open (AIR 1715, ours {@link Outgoing#WiredLinkOpenComposer}): the owner used their Link;
 * the client answers with a configuration request for it.
 */
public class WiredLinkOpenComposer extends MessageComposer {
    private final int furniId;

    public WiredLinkOpenComposer(int furniId) {
        this.furniId = furniId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredLinkOpenComposer);
        this.response.appendInt(this.furniId);
        return this.response;
    }
}
