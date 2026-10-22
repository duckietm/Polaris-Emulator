package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 bonus claim result (1048): {@code collectionId, wallet, success}. */
public class CollectionBonusClaimResultComposer extends MessageComposer {
    private final String collectionId;
    private final String wallet;
    private final boolean success;

    public CollectionBonusClaimResultComposer(String collectionId, String wallet, boolean success) {
        this.collectionId = collectionId;
        this.wallet = wallet;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectionBonusClaimResultComposer);
        this.response.appendString(this.collectionId);
        this.response.appendString(this.wallet);
        this.response.appendBoolean(this.success);
        return this.response;
    }
}
