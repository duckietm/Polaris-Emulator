package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 silver balance (2458): the silver the transfer fee and relic stamps cost. */
public class CollectibleSilverBalanceComposer extends MessageComposer {
    private final int balance;

    public CollectibleSilverBalanceComposer(int balance) {
        this.balance = balance;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleSilverBalanceComposer);
        this.response.appendInt(this.balance);
        return this.response;
    }
}
