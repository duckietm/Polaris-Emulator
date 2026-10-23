package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.CatalogPaymentService;
import com.eu.habbo.habbohotel.catalog.CatalogPurchaseMath;
import com.eu.habbo.habbohotel.catalog.ClubOffer;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.subscriptions.Subscription;
import com.eu.habbo.habbohotel.users.subscriptions.SubscriptionHabboClub;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;

public class CatalogBuyClubDiscountEvent extends MessageHandler {

    /** What one purchase costs and gives, checked to be non-negative. */
    private record Price(int days, int credits, int points, int pointsType, int seconds) {}

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        int offerId = this.packet.readInt();

        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        ClubOffer deal = catalog.clubOffers.get(offerId);
        if (!this.isAccepted(deal)) return;
        if (this.regularOf(catalog, deal) == null) return;

        Price price = this.priceOf(deal);
        if (price == null) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (price.days() > 0) {
            this.buy(price);
        }
    }

    /**
     * The extend window (352) offers the deal inside the discount window, otherwise a normal HC offer at full price;
     * accept exactly those.
     */
    private boolean isAccepted(ClubOffer deal) {
        boolean inDiscountWindow = this.inDiscountWindow();
        return deal != null && (deal.isDeal() ? inDiscountWindow : deal.isHabboClubOffer());
    }

    /** Whether the buyer's club time ends within the discount window; the last partial day counts as one. */
    private boolean inDiscountWindow() {
        Subscription subscription =
                this.client.getHabbo().getHabboStats().getSubscription(SubscriptionHabboClub.HABBO_CLUB);

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

        return timeRemaining > 0
                && SubscriptionHabboClub.DISCOUNT_ENABLED
                && days <= SubscriptionHabboClub.DISCOUNT_DAYS_BEFORE_END;
    }

    /** The regular HC offer a deal discounts, of the same length; a regular offer is its own. */
    private ClubOffer regularOf(CatalogManager catalog, ClubOffer deal) {
        return deal.isDeal()
                ? catalog.getClubOffers().stream()
                        .filter(x -> x.getDays() == deal.getDays())
                        .findAny()
                        .orElse(null)
                : deal;
    }

    private Price priceOf(ClubOffer deal) {
        try {
            int days = CatalogPurchaseMath.requireNonNegative(deal.getDays(), "discount club duration");
            int credits = CatalogPurchaseMath.requireNonNegative(deal.getCredits(), "discount club credit price");
            int points = CatalogPurchaseMath.requireNonNegative(deal.getPoints(), "discount club points price");
            return new Price(
                    days, credits, points, deal.getPointsType(), CatalogPurchaseMath.checkedSubscriptionSeconds(days));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void buy(Price price) throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo.getHabboInfo().getCurrencyAmount(price.pointsType()) < price.points()) return;
        if (habbo.getHabboInfo().getCredits() < price.credits()) return;

        // Taken in one ledger mutation that no plugin event can cancel or shrink, as the catalog takes any club offer.
        int paidCredits = habbo.hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : price.credits();
        int paidPoints = habbo.hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : price.points();
        if (!CatalogPaymentService.tryTake(habbo, paidCredits, price.pointsType(), paidPoints)) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (habbo.getHabboStats().createSubscription(Subscription.HABBO_CLUB, price.seconds()) == null) {
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            throw new Exception("Unable to create or extend subscription");
        }

        this.client.sendResponse(new PurchaseOKComposer(null));
        this.client.sendResponse(new InventoryRefreshComposer());

        habbo.getHabboStats().run();
    }
}
