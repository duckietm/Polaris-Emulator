package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceItemInfoComposer;

public class RequestItemInfoEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.packet.readInt();
        int id = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(id)) {
            return;
        }

        this.client.sendResponse(new MarketplaceItemInfoComposer(id));
    }
}
