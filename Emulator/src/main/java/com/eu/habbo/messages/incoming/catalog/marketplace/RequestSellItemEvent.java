package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketplaceTokenService;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;

/**
 * The can-make-offer check (848) the official client sends before it opens the make-offer window. The answer
 * carries a result code and the seller's token count: 1 opens the window, 2 says selling is not allowed and 4
 * opens the buy-tokens window ({@code MarketplaceModel.proceedOfferMaking}).
 */
public class RequestSellItemEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        // A closed marketplace is "not allowed"; NO_TRADE_PASS would tell the user to buy a pass.
        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceSellItemComposer(MarketplaceSellItemComposer.NOT_ALLOWED, 0));
            return;
        }

        MarketplaceTokenService tokens = MarketplaceTokenService.installed();
        Habbo habbo = this.client.getHabbo();
        if (tokens == null || habbo == null || !tokens.settings().enabled()) {
            this.client.sendResponse(new MarketplaceSellItemComposer(MarketplaceSellItemComposer.ALLOWED, 0));
            return;
        }

        int balance = tokens.tokens(habbo.getHabboInfo().getId());
        this.client.sendResponse(new MarketplaceSellItemComposer(
                balance > 0 ? MarketplaceSellItemComposer.ALLOWED : MarketplaceSellItemComposer.NO_ADS_LEFT, balance));
    }
}
