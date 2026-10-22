package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/** AIR 15 completion reward claim result (501): {@code collectionId, wallet, success}. */
public class CollectionRewardClaimResultComposer extends MessageComposer {
    private final String collectionId;
    private final String wallet;
    private final boolean success;

    public CollectionRewardClaimResultComposer(String collectionId, String wallet, boolean success) {
        this.collectionId = collectionId;
        this.wallet = wallet;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectionRewardClaimResultComposer);
        this.response.appendString(this.collectionId);
        this.response.appendString(this.wallet);
        this.response.appendBoolean(this.success);
        return this.response;
    }
}
