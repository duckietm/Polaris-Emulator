package com.eu.habbo.messages.incoming.gamecenter;

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

        if (!offers.addExtraGames(habbo.getHabboInfo().getId(), offer.games())) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (offer.priceInCredits() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_CREDITS)) {
            habbo.giveCredits(-offer.priceInCredits());
        }
        if (offer.priceInPoints() > 0 && !habbo.hasPermission(Permission.ACC_INFINITE_POINTS)) {
            habbo.givePoints(offer.pointsType(), -offer.priceInPoints());
        }

        this.client.sendResponse(new PurchaseOKComposer());
        this.client.sendResponse(new GameCenterAccountInfoComposer(
                0, manager.getGamesLeft(habbo.getHabboInfo().getId())));
    }
}
