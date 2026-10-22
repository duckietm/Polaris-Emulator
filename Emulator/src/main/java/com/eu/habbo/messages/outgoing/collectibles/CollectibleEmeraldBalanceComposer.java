package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 emerald balance (652): the emeralds the guild shop spends. */
public class CollectibleEmeraldBalanceComposer extends MessageComposer {
    private final int balance;

    public CollectibleEmeraldBalanceComposer(int balance) {
        this.balance = balance;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleEmeraldBalanceComposer);
        this.response.appendInt(this.balance);
        return this.response;
    }
}
