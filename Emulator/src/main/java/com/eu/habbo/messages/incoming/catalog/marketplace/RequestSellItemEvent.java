package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;

public class RequestSellItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        // A closed marketplace is "not allowed"; NO_TRADE_PASS would tell the user to buy a pass.
        int result = MarketPlace.MARKETPLACE_ENABLED
                ? MarketplaceSellItemComposer.ALLOWED
                : MarketplaceSellItemComposer.NOT_ALLOWED;

        this.client.sendResponse(new MarketplaceSellItemComposer(result, 0));
    }
}
