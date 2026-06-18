package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;

public class BuyItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(offerId)) {
            return;
        }

        MarketPlace.buyItem(offerId, this.client);
    }
}
