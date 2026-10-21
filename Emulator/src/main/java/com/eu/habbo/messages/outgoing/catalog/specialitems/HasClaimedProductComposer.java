package com.eu.habbo.messages.outgoing.catalog.specialitems;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 15 {@code class_2859} / {@code class_2841} (266): {@code (claimId, hasClaimed)}. The special items
 * display answers its has-claimed request with it: {@code true} shows the claim button as claimed,
 * {@code false} lets the user browse every item and then claim.
 */
public class HasClaimedProductComposer extends MessageComposer {
    private final String claimId;
    private final boolean claimed;

    public HasClaimedProductComposer(String claimId, boolean claimed) {
        this.claimId = claimId;
        this.claimed = claimed;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HasClaimedProductComposer);
        this.response.appendString(this.claimId);
        this.response.appendBoolean(this.claimed);
        return this.response;
    }
}
