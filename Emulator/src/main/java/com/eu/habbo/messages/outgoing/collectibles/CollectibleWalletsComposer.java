package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR 15 {@code class_2901} (400): the collector ("Stardust") wallet, then the linked wallets. A hotel
 * without a blockchain has only the user's collector wallet, so the list after it is empty.
 */
public class CollectibleWalletsComposer extends MessageComposer {
    private final String collectorWallet;

    public CollectibleWalletsComposer(String collectorWallet) {
        this.collectorWallet = collectorWallet;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleWalletsComposer);
        this.response.appendString(this.collectorWallet);
        this.response.appendInt(0);
        return this.response;
    }
}
