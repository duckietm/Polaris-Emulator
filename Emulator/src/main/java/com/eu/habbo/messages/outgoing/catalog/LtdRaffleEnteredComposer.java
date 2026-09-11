package com.eu.habbo.messages.outgoing.catalog;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 13 event 933 (`HabboCatalog.onLtdRaffleEntered`): the purchase of a limited-edition
 * item joined a raffle instead of completing straight away, so the purchase confirmation
 * keeps its "hold on, we are processing your LTD purchase" line running until the result
 * arrives. The payload is the class name of the item being raffled.
 */
public class LtdRaffleEnteredComposer extends MessageComposer {
    private final String className;

    public LtdRaffleEnteredComposer(String className) {
        this.className = className == null ? "" : className;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.LtdRaffleEnteredComposer);
        this.response.appendString(this.className);
        return this.response;
    }

    public String getClassName() {
        return this.className;
    }
}
