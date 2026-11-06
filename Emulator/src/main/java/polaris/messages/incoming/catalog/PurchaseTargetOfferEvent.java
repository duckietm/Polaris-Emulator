package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogItem;
import polaris.hotel.catalog.CatalogManager;
import polaris.hotel.catalog.TargetOffer;
import polaris.hotel.users.User;
import polaris.hotel.users.cache.UserOfferPurchase;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.NotEnoughPointsTypeComposer;

public class PurchaseTargetOfferEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog.purchase";
    }

    @Override
    public void handle() throws Exception {
        final int offerId = this.packet.readInt();
        int amount = this.packet.readInt();

        if (amount <= 0 || offerId <= 0) return;

        if (Emulator.getIntUnixTimestamp() - this.client.getUser().getUserStats().lastPurchaseTimestamp
                >= CatalogManager.PURCHASE_COOLDOWN) {
            this.client.getUser().getUserStats().lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();

            TargetOffer offer =
                    Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(offerId);
            if (offer == null) return;

            UserOfferPurchase purchase = UserOfferPurchase.getOrCreate(this.client.getUser(), offerId);

            if (purchase != null) {
                amount = TargetOfferPurchaseGuard.purchasableAmount(
                        amount, offer.getPurchaseLimit(), purchase.getAmount());
                if (amount <= 0) return;

                int now = Emulator.getIntUnixTimestamp();
                if (offer.getExpirationTime() > now) {
                    CatalogItem item =
                            Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(offer.getCatalogItem());
                    if (item == null) return;
                    if (item.isLimited()) {
                        amount = 1;
                    }

                    int delivered = this.buyAtOfferPrice(offer, item, amount);

                    // The limit counts what was delivered: it used to be taken before the purchase, which could fail.
                    if (delivered > 0) purchase.update(delivered, now);
                }
            }
        }
    }

    /**
     * Buys the offer at its own price: the catalog item's price was charged before, whatever the offer said. The full
     * price is taken first, each copy is delivered free, and the price of the copies that were not delivered is given
     * back. Returns how many were delivered.
     */
    private int buyAtOfferPrice(TargetOffer offer, CatalogItem item, int amount) {
        User user = this.client.getUser();
        long credits = (long) Math.max(0, offer.getPriceInCredits()) * amount;
        long points = (long) Math.max(0, offer.getPriceInActivityPoints()) * amount;
        int pointsType = offer.getActivityPointsType();

        if (credits > Integer.MAX_VALUE || points > Integer.MAX_VALUE) return 0;

        if (user.getUserInfo().getCredits() < credits
                || (points > 0 && user.getUserInfo().getCurrencyAmount(pointsType) < points)) {
            this.client.sendResponse(new NotEnoughPointsTypeComposer(
                    user.getUserInfo().getCredits() < credits,
                    points > 0 && user.getUserInfo().getCurrencyAmount(pointsType) < points,
                    pointsType));
            return 0;
        }

        if (credits > 0 && !user.tryTakeCredits((int) credits)) return 0;

        if (points > 0 && !user.tryTakePoints(pointsType, (int) points)) {
            if (credits > 0) user.giveCredits((int) credits, "catalog.targetoffer.refund");
            return 0;
        }

        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        int delivered = 0;

        while (delivered < amount && catalog.purchaseItemDelivered(null, item, user, 1, "", true)) {
            delivered++;
        }

        int missing = amount - delivered;

        if (missing > 0) {
            if (credits > 0) user.giveCredits(offer.getPriceInCredits() * missing, "catalog.targetoffer.refund");
            if (points > 0) {
                user.givePoints(pointsType, offer.getPriceInActivityPoints() * missing, "catalog.targetoffer.refund");
            }
        }

        return delivered;
    }
}
