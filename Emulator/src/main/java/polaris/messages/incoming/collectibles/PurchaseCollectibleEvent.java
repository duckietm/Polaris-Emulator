package polaris.messages.incoming.collectibles;

import java.util.List;
import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.Owned;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.CollectiblePurchaseResultComposer;
import polaris.messages.outgoing.collectibles.CollectibleShopOffersComposer;

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
        User user = this.client.getUser();
        if (user == null) return;

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
                && CollectiblesSupport.ownsWallet(user, wallet);
        if (!forSale) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        int price = product.get().emeraldPrice();
        if (!CollectiblesSupport.pay(user, settings.emeraldType(), price, "collectibles.shop")) {
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        Optional<Owned> owned;
        try {
            owned = service.mint(user.getUserInfo().getId(), productCode, "shop", CollectiblesSupport.now());
        } catch (Exception exception) {
            // The emeralds are already taken: a mint that throws refunds them, as one that finds nothing does.
            CollectiblesSupport.refund(user, settings.emeraldType(), price, "collectibles.shop.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            throw exception;
        }
        if (owned.isEmpty()) {
            CollectiblesSupport.refund(user, settings.emeraldType(), price, "collectibles.shop.refund");
            this.client.sendResponse(new CollectiblePurchaseResultComposer(1));
            return;
        }

        CollectiblesSupport.publishAcquired(user, List.of(owned.get()), service, settings);
        this.client.sendResponse(new CollectiblePurchaseResultComposer(0));
        CollectiblesSupport.sendBalances(user, settings);
        this.client.sendResponse(new CollectibleShopOffersComposer(service.shopOffers()));
    }
}
