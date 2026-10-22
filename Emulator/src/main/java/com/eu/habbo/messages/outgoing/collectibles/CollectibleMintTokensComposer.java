package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 stamp balance (official 1155, taken here: 9422): the relic stamps of the user. */
public class CollectibleMintTokensComposer extends MessageComposer {
    private final int amount;

    public CollectibleMintTokensComposer(int amount) {
        this.amount = amount;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleMintTokensComposer);
        this.response.appendInt(this.amount);
        return this.response;
    }
}
