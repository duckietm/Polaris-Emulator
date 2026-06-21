package com.eu.habbo.messages.incoming.catalog;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.catalog.CatalogManager;
import com.eu.habbo.habbohotel.catalog.TargetOffer;
import com.eu.habbo.habbohotel.users.cache.HabboOfferPurchase;
import com.eu.habbo.messages.incoming.MessageHandler;

public class PurchaseTargetOfferEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 1000;
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
                    purchase.update(amount, now);
                    Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, this.client.getHabbo(), amount, "", false);

                }
            }
        }
    }
}
