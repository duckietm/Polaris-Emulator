package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.cache.HabboOfferPurchase;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer;

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


        if (Emulator.getIntUnixTimestamp() - this.client.getHabbo().getHabboStats().lastPurchaseTimestamp >= CatalogManager.PURCHASE_COOLDOWN) {
            this.client.getHabbo().getHabboStats().lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();

            TargetOffer offer = Emulator.getGameEnvironment().getCatalogManager().getTargetOffer(offerId);
            if (offer == null) return;

            HabboOfferPurchase purchase = HabboOfferPurchase.getOrCreate(this.client.getHabbo(), offerId);

            if (purchase != null) {
                amount = TargetOfferPurchaseGuard.purchasableAmount(amount, offer.getPurchaseLimit(), purchase.getAmount());
                if (amount <= 0) return;

                int now = Emulator.getIntUnixTimestamp();
                if (offer.getExpirationTime() > now) {
                    CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(offer.getCatalogItem());
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
        Habbo habbo = this.client.getHabbo();
        long credits = (long) Math.max(0, offer.getPriceInCredits()) * amount;
        long points = (long) Math.max(0, offer.getPriceInActivityPoints()) * amount;
        int pointsType = offer.getActivityPointsType();

        if (credits > Integer.MAX_VALUE || points > Integer.MAX_VALUE) return 0;

        if (habbo.getHabboInfo().getCredits() < credits
                || (points > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < points)) {
            this.client.sendResponse(new NotEnoughPointsTypeComposer(
                    habbo.getHabboInfo().getCredits() < credits,
                    points > 0 && habbo.getHabboInfo().getCurrencyAmount(pointsType) < points,
                    pointsType));
            return 0;
        }

        if (credits > 0 && !habbo.tryTakeCredits((int) credits)) return 0;

        if (points > 0 && !habbo.tryTakePoints(pointsType, (int) points)) {
            if (credits > 0) habbo.giveCredits((int) credits, "catalog.targetoffer.refund");
            return 0;
        }

        CatalogManager catalog = Emulator.getGameEnvironment().getCatalogManager();
        int delivered = 0;

        while (delivered < amount && catalog.purchaseItemDelivered(null, item, habbo, 1, "", true)) {
            delivered++;
        }

        int missing = amount - delivered;

        if (missing > 0) {
            if (credits > 0) habbo.giveCredits(offer.getPriceInCredits() * missing, "catalog.targetoffer.refund");
            if (points > 0) {
                habbo.givePoints(pointsType, offer.getPriceInActivityPoints() * missing, "catalog.targetoffer.refund");
            }
        }

        return delivered;
    }
}
