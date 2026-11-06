package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.catalog.marketplace.MarketplaceTokenService;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;

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
        User user = this.client.getUser();
        if (tokens == null || user == null || !tokens.settings().enabled()) {
            this.client.sendResponse(new MarketplaceSellItemComposer(MarketplaceSellItemComposer.ALLOWED, 0));
            return;
        }

        int balance = tokens.tokens(user.getUserInfo().getId());
        this.client.sendResponse(new MarketplaceSellItemComposer(
                balance > 0 ? MarketplaceSellItemComposer.ALLOWED : MarketplaceSellItemComposer.NO_ADS_LEFT, balance));
    }
}
