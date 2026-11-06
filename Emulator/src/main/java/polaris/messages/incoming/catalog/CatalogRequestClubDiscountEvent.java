package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.ClubOffer;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.ClubExtendOfferComposer;

public class CatalogRequestClubDiscountEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {

        Subscription subscription = this.client.getUser().getUserStats().getSubscription(SubscriptionClub.CLUB);

        int days = 0;
        int minutes = 0;
        int timeRemaining = 0;

        if (subscription != null) {
            timeRemaining = subscription.getRemaining();
            days = (int) Math.floor(timeRemaining / 86400.0);
            minutes = (int) Math.ceil(timeRemaining / 60.0);

            if (days < 1 && minutes > 0) {
                days = 1;
            }
        }

        if (timeRemaining > 0
                && SubscriptionClub.DISCOUNT_ENABLED
                && days <= SubscriptionClub.DISCOUNT_DAYS_BEFORE_END) {
            ClubOffer deal = Emulator.getGameEnvironment().getCatalogManager().clubOffers.values().stream()
                    .filter(ClubOffer::isDeal)
                    .findAny()
                    .orElse(null);

            if (deal != null) {
                ClubOffer regular = Emulator.getGameEnvironment().getCatalogManager().getClubOffers().stream()
                        .filter(x -> x.getDays() == deal.getDays())
                        .findAny()
                        .orElse(null);
                if (regular != null) {
                    this.client.sendResponse(new ClubExtendOfferComposer(
                            this.client.getUser(),
                            deal,
                            regular.getCredits(),
                            regular.getPoints(),
                            regular.getPointsType(),
                            Math.max(0, days)));
                }
            }
        }
    }
}
