package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceCancelAllOffersComposer;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOwnItemsComposer;
import java.util.List;

/**
 * AIR 13 composer 1228 (`HabboCatalog.cancelAllMarketPlaceOffers`), the "recall all"
 * button of the own-offers window: pulls every still-open offer back at once.
 */
public class CancelAllOwnItemsEvent extends MessageHandler {

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
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceCancelAllOffersComposer(List.of(), false));
            return;
        }

        List<Integer> recalled = MarketPlace.takeBackAllItems(this.client.getHabbo());

        this.client.sendResponse(new MarketplaceCancelAllOffersComposer(recalled, true));
        this.client.sendResponse(new MarketplaceOwnItemsComposer(this.client.getHabbo()));
    }
}
