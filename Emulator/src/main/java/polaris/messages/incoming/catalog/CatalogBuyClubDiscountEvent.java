package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.CatalogPaymentService;
import polaris.hotel.catalog.CatalogPurchaseMath;
import polaris.hotel.catalog.ClubOffer;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.subscriptions.Subscription;
import polaris.hotel.users.subscriptions.SubscriptionClub;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;
import polaris.messages.outgoing.catalog.PurchaseOKComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;

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
        // Every refusal answers: the extend window waits for one, and silence left it hanging.
        if (!this.isAccepted(deal) || this.regularOf(catalog, deal) == null) {
            this.refuse();
            return;
        }

        Price price = this.priceOf(deal);
        if (price == null) {
            this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR));
            return;
        }

        if (price.days() > 0) {
            this.buy(price);
        } else {
            // An offer of no days gives nothing.
            this.refuse();
        }
    }

    private void refuse() {
        this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
    }

    /**
     * The extend window (352) offers the deal inside the discount window, otherwise a normal HC offer at full price;
     * accept exactly those.
     */
    private boolean isAccepted(ClubOffer deal) {
        boolean inDiscountWindow = this.inDiscountWindow();
        return deal != null && (deal.isDeal() ? inDiscountWindow : deal.isClubOffer());
    }

    /** Whether the buyer's club time ends within the discount window; the last partial day counts as one. */
    private boolean inDiscountWindow() {
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

        return timeRemaining > 0
                && SubscriptionClub.DISCOUNT_ENABLED
                && days <= SubscriptionClub.DISCOUNT_DAYS_BEFORE_END;
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

    private void buy(Price price) {
        User user = this.client.getUser();
        boolean notEnoughCredits = user.getUserInfo().getCredits() < price.credits();
        boolean notEnoughPoints = user.getUserInfo().getCurrencyAmount(price.pointsType()) < price.points();
        if (notEnoughCredits || notEnoughPoints) {
            this.client.sendResponse(
                    new NotEnoughPointsTypeComposer(notEnoughCredits, notEnoughPoints, price.pointsType()));
            return;
        }

        // Taken in one ledger mutation that no plugin event can cancel or shrink, as the catalog takes any club offer.
        int paidCredits = user.hasPermission(Permission.ACC_INFINITE_CREDITS) ? 0 : price.credits();
        int paidPoints = user.hasPermission(Permission.ACC_INFINITE_POINTS) ? 0 : price.points();
        if (!CatalogPaymentService.tryTake(user, paidCredits, price.pointsType(), paidPoints)) {
            this.client.sendResponse(new AlertPurchaseUnavailableComposer(AlertPurchaseUnavailableComposer.ILLEGAL));
            return;
        }

        if (user.getUserStats().createSubscription(Subscription.CLUB, price.seconds()) == null) {
            // Paid for nothing otherwise: the price goes back before the buyer is told.
            CatalogPaymentService.refund(user, paidCredits, price.pointsType(), paidPoints);
            this.client.sendResponse(
                    new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
            return;
        }

        this.client.sendResponse(new PurchaseOKComposer(null));
        this.client.sendResponse(new InventoryRefreshComposer());

        user.getUserStats().run();
    }
}
