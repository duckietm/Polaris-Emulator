package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.ExtendClubMessageComposer;
import java.util.Comparator;
import java.util.List;

/**
 * AIR 13 composer 352 (`ClubDiscountPromoExtension.onTextRegionClicked` /
 * `CitizenshipVipDiscountPromoExtension.onButtonClicked`): the player clicked the
 * "your club is running out" promo in the toolbar, so the server answers with the offer
 * that would extend the subscription and the client shows the extend confirmation before
 * anything is charged.
 *
 * <p>Same offer the automatic discount promo uses (`CatalogRequestClubDiscountEvent`): the
 * club deal, priced against the normal offer of the same length so the window can show
 * what the deal saves. Unlike that promo this is an explicit click, so when no deal
 * applies the player still gets the confirmation, on the cheapest normal offer at full
 * price.
 */
public class RequestClubExtendConfirmEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Subscription subscription =
                this.client.getHabbo().getHabboStats().getSubscription(SubscriptionHabboClub.HABBO_CLUB);

        int remaining = subscription == null ? 0 : subscription.getRemaining();
        int daysRemaining = remaining <= 0 ? 0 : Math.max(1, (int) Math.floor(remaining / 86400.0));

        List<ClubOffer> regularOffers = Emulator.getGameEnvironment().getCatalogManager().getClubOffers().stream()
                .filter(ClubOffer::isHabboClubOffer)
                .sorted(Comparator.comparingInt(ClubOffer::getCredits))
                .toList();

        ClubOffer deal = null;
        if (SubscriptionHabboClub.DISCOUNT_ENABLED) {
            deal = Emulator.getGameEnvironment().getCatalogManager().clubOffers.values().stream()
                    .filter(ClubOffer::isDeal)
                    .filter(ClubOffer::isHabboClubOffer)
                    .min(Comparator.comparingInt(ClubOffer::getCredits))
                    .orElse(null);
        }

        ClubOffer offer =
                deal != null ? deal : regularOffers.stream().findFirst().orElse(null);

        if (offer == null) return;

        ClubOffer regular = regularOffers.stream()
                .filter(candidate -> candidate.getDays() == offer.getDays())
                .findFirst()
                .orElse(offer);

        this.client.sendResponse(new ExtendClubMessageComposer(
                this.client.getHabbo(),
                offer,
                regular.getCredits(),
                regular.getPoints(),
                regular.getPointsType(),
                daysRemaining));
    }
}
