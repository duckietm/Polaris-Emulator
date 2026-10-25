package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceBuyErrorComposer;

public class BuyItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public String getRatelimitGroup() {
        return "marketplace.mutation";
    }

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(offerId)) {
            return;
        }

        // Every other marketplace packet refuses while the marketplace is off; buying
        // was the one that did not, so offers could still be bought by id.
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(
                    new MarketplaceBuyErrorComposer(MarketplaceBuyErrorComposer.SOLD_OUT, 0, offerId, 0));
            return;
        }

        MarketPlace.buyItem(offerId, this.client);
    }
}
