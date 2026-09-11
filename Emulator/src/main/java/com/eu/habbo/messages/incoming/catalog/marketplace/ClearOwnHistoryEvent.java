package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceClearOwnHistoryComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer;

/**
 * AIR 13 composer 2058 (`HabboCatalog.clearOwnMarketPlaceHistory`), the "clear history"
 * button of the own-offers window. The payload is the tab being cleared: sold ({@code 2})
 * or expired ({@code 3}); the official client never sends anything else.
 */
public class ClearOwnHistoryEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public String getRatelimitGroup() {
        return "marketplace.mutation";
    }

    @Override
    public void handle() throws Exception {
        int state = this.packet.readInt();

        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceClearOwnHistoryComposer(false));
            return;
        }

        boolean cleared = MarketPlace.clearOwnHistory(this.client, state);

        this.client.sendResponse(new MarketplaceClearOwnHistoryComposer(cleared));

        if (cleared) {
            this.client.sendResponse(new MarketplaceOwnItemsComposer(this.client.getHabbo()));
        }
    }
}
