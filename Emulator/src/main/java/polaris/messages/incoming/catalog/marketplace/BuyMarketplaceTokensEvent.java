package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.catalog.marketplace.MarketplaceTokenService;
import polaris.hotel.economy.EconomyLedger;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceSellItemComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;

/**
 * Buy marketplace tokens (1866, no fields), the buy button of the official {@code buy_marketplace_tokens}
 * window. Charges one batch's price in credits and adds its tokens, then answers the can-make-offer result
 * again (1 with the new token count), so the client goes on to the make-offer window. A user who cannot
 * pay gets the not-enough-credits answer and nothing changes.
 */
public class BuyMarketplaceTokensEvent extends MessageHandler {
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
        User user = this.client.getUser();
        if (user == null) return;

        if (!MarketPlace.MARKETPLACE_ENABLED) {
            this.client.sendResponse(new MarketplaceSellItemComposer(MarketplaceSellItemComposer.NOT_ALLOWED, 0));
            return;
        }

        MarketplaceTokenService tokens = MarketplaceTokenService.installed();
        if (tokens == null || !tokens.settings().enabled()) {
            // Selling is free: nothing to buy, the client may go straight on.
            this.client.sendResponse(new MarketplaceSellItemComposer(MarketplaceSellItemComposer.ALLOWED, 0));
            return;
        }

        if (user.getUserInfo().getCredits() < tokens.settings().price()) {
            this.client.sendResponse(new NotEnoughPointsTypeComposer(true, false, 0));
            return;
        }

        MarketplaceTokenService.Purchase purchase;
        try {
            purchase = LedgerWalletMutation.coordinated(user, () -> {
                MarketplaceTokenService.Purchase committed =
                        tokens.buy(user.getUserInfo().getId());
                if (committed.creditsAfter() >= 0) {
                    LedgerWalletMutation.applyCommitted(user, EconomyLedger.CREDITS, committed.creditsAfter());
                }
                return committed;
            });
        } catch (IllegalArgumentException exception) {
            this.client.sendResponse(new NotEnoughPointsTypeComposer(true, false, 0));
            return;
        }

        if (purchase.creditsAfter() >= 0) {
            this.client.sendResponse(new UserCreditsComposer(user));
        }
        this.client.sendResponse(
                new MarketplaceSellItemComposer(MarketplaceSellItemComposer.ALLOWED, purchase.tokens()));
    }
}
