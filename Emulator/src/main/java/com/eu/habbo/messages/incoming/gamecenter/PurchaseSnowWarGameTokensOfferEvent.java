package com.eu.habbo.messages.incoming.gamecenter;

import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarManager;
import com.eu.habbo.habbohotel.games.snowwar.SnowWarTokenOfferRepository;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.gamecenter.GameCenterAccountInfoComposer;

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

        Habbo habbo = this.client.getHabbo();
        if (habbo == null) {
            return;
        }

        SnowWarManager manager = SnowWarManager.getInstance();
        SnowWarTokenOfferRepository offers = manager.getTokenOffers();
        SnowWarTokenOfferRepository.Offer offer = offers.findOffer(offerId);

        if (offer == null || offer.games() <= 0) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (habbo.getHabboInfo().getCredits() < offer.priceInCredits()
                || habbo.getHabboInfo().getCurrencyAmount(offer.pointsType()) < offer.priceInPoints()) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        // The price is taken first, in one ledger mutation no plugin event can cancel or
        // shrink. The games used to be credited before the debit, which went through the
        // cancellable giveCredits/givePoints path, so a cancelled or failed debit left
        // the games free.
        int paidCredits = habbo.hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : offer.priceInCredits();
        int paidPoints = habbo.hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : offer.priceInPoints();
        if (!CatalogPaymentService.tryTake(habbo, paidCredits, offer.pointsType(), paidPoints)) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (!offers.addExtraGames(habbo.getHabboInfo().getId(), offer.games())) {
            // Paid for nothing otherwise: the price goes back before the buyer is told.
            CatalogPaymentService.refund(habbo, paidCredits, offer.pointsType(), paidPoints);
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        this.client.sendResponse(new PurchaseOKComposer());
        this.client.sendResponse(new GameCenterAccountInfoComposer(
                0, manager.getGamesLeft(habbo.getHabboInfo().getId())));
    }
}
