package polaris.messages.incoming.gamecenter;

import polaris.hotel.catalog.CatalogPaymentService;
import polaris.hotel.games.snowwar.SnowWarManager;
import polaris.hotel.games.snowwar.SnowWarTokenOfferRepository;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;

/**
 * AIR PurchaseSnowWarGameTokensOfferComposer (391): buys one "get more games"
 * offer, charges its credits and activity points and credits the extra games.
 */
public class PurchaseSnowWarGameTokensOfferEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        SnowWarManager manager = SnowWarManager.getInstance();
        SnowWarTokenOfferRepository offers = manager.getTokenOffers();
        SnowWarTokenOfferRepository.Offer offer = offers.findOffer(offerId);

        if (offer == null || offer.games() <= 0) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (user.getUserInfo().getCredits() < offer.priceInCredits()
                || user.getUserInfo().getCurrencyAmount(offer.pointsType()) < offer.priceInPoints()) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        // The price is taken first, in one ledger mutation no plugin event can cancel or
        // shrink. The games used to be credited before the debit, which went through the
        // cancellable giveCredits/givePoints path, so a cancelled or failed debit left
        // the games free.
        int paidCredits = user.hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : offer.priceInCredits();
        int paidPoints = user.hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : offer.priceInPoints();
        if (!CatalogPaymentService.tryTake(user, paidCredits, offer.pointsType(), paidPoints)) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (!offers.addExtraGames(user.getUserInfo().getId(), offer.games())) {
            // Paid for nothing otherwise: the price goes back before the buyer is told.
            CatalogPaymentService.refund(user, paidCredits, offer.pointsType(), paidPoints);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        this.client.sendResponse(new PurchaseOKComposer());
        this.client.sendResponse(new GameCenterAccountInfoComposer(
                0, manager.getGamesLeft(user.getUserInfo().getId())));
    }
}
