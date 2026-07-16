package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseMath;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;

public class CatalogBuyClubDiscountEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        Subscription subscription = this.client.getHabbo().getHabboStats().getSubscription(SubscriptionHabboClub.HABBO_CLUB);

        int days = 0;
        int minutes = 0;
        int timeRemaining = 0;

        if(subscription != null) {
            timeRemaining = subscription.getRemaining();
            days = (int) Math.floor(timeRemaining / 86400.0);
            minutes = (int) Math.ceil(timeRemaining / 60.0);

            if(days < 1 && minutes > 0) {
                days = 1;
            }
        }

        if(timeRemaining > 0 && SubscriptionHabboClub.DISCOUNT_ENABLED && days <= SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END) {
            ClubOffer deal = Emulator.getGameEnvironment().getCatalogManager().clubOffers.get(offerId);

            if(deal != null && deal.isDeal()) {
                ClubOffer regular = Emulator.getGameEnvironment().getCatalogManager().getClubOffers().stream().filter(x -> x.getDays() == deal.getDays()).findAny().orElse(null);
                if(regular != null) {

                    int totalDays;
                    int totalCredits;
                    int totalDuckets;
                    int subscriptionSeconds;
                    try {
                        totalDays = CatalogPurchaseMath.requireNonNegative(deal.getDays(), "discount club duration");
                        totalCredits = CatalogPurchaseMath.requireNonNegative(deal.getCredits(), "discount club credit price");
                        totalDuckets = CatalogPurchaseMath.requireNonNegative(deal.getPoints(), "discount club points price");
                        subscriptionSeconds = CatalogPurchaseMath.checkedSubscriptionSeconds(totalDays);
                    } catch (IllegalArgumentException e) {
                        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
                        return;
                    }

                    if (totalDays > 0) {
                        if (this.client.getHabbo().getHabboInfo().getCurrencyAmount(deal.getPointsType()) < totalDuckets)
                            return;

                        if (this.client.getHabbo().getHabboInfo().getCredits() < totalCredits)
                            return;

                        if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_CREDITS))
                            this.client.getHabbo().giveCredits(-totalCredits);

                        if (!this.client.getHabbo().hasPermission(Permission.ACC_INFINITE_POINTS))
                            this.client.getHabbo().givePoints(deal.getPointsType(), -totalDuckets);


                        if(this.client.getHabbo().getHabboStats().createSubscription(Subscription.HABBO_CLUB, subscriptionSeconds) == null) {
                            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
                            throw new Exception("Unable to create or extend subscription");
                        }

                        this.client.sendResponse(new PurchaseOKComposer(null));
                        this.client.sendResponse(new InventoryRefreshComposer());

                        this.client.getHabbo().getHabboStats().run();
                    }
                }
            }
        }

    }
}
