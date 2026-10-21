package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceItemInfoComposer;

/**
 * GetMarketplaceItemStats (3288): the offer details panel asks for the price history of a furni.
 * The client sends the item category (1 floor, 2 wall, 3 unique) and the sprite id, and drops any
 * answer whose category and sprite id differ from what it asked for, so both are echoed back.
 */
public class RequestItemInfoEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        int category = this.packet.readInt();
        int id = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(id)) {
            return;
        }

        this.client.sendResponse(new MarketplaceItemInfoComposer(category, id));
    }
}
