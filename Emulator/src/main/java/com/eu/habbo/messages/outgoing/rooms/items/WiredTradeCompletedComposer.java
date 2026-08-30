package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * The negotiation settled. Carries nothing: what changed hands has already been pushed through the
 * inventory and currency updates the client acts on anyway.
 */
public class WiredTradeCompletedComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredTradeCompletedComposer);
        return this.response;
    }
}
