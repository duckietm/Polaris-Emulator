package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Owned;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.Product;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.CollectiblePurchaseResultComposer;
import com.eu.habbo.messages.outgoing.collectibles.CollectibleShopOffersComposer;
import java.util.List;
import java.util.Optional;

/**
 * AIR 15 collectible purchase (1542, {@code productCode, wallet}), the purchase confirmation of a product
 * of type {@code n}: takes the emerald price and mints the next serial. Answers {@code 169} (0 ok, 1 error),
 * the new balances and the shop with the new mint count.
 */
public class PurchaseCollectibleEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        String productCode = this.packet.readString();
        String wallet = this.packet.readString();

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null) return;

        Optional<Product> product = service.product(productCode);
        boolean forSale = product.isPresent()
                && product.get().shopEnabled()
                && product.get().emeraldPrice() > 0
                && product.get().canMintMore()
                && CollectiblesSupport.ownsWallet(habbo, wallet);
        if (!forSale) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int price = product.get().emeraldPrice();
        if (!CollectiblesSupport.pay(habbo, settings.emeraldType(), price, "collectibles.shop")) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        Optional<Owned> owned =
                service.mint(habbo.getHabboInfo().getId(), productCode, "shop", CollectiblesSupport.now());
        if (owned.isEmpty()) {
            CollectiblesSupport.refund(habbo, settings.emeraldType(), price, "collectibles.shop.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        CollectiblesSupport.publishAcquired(habbo, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectiblePurchaseResultComposer(0));
        CollectiblesSupport.sendBalances(habbo, settings);
        this.client.sendResponse(new CollectibleShopOffersComposer(service.shopOffers()));
    }
}
