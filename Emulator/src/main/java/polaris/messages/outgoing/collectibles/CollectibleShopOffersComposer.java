package polaris.messages.outgoing.collectibles;

import java.util.List;
import polaris.hotel.collectibles.CollectibleData.Product;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 15 guild shop offers (3495): product code, emerald price, two flags (new, sold out), the mint limit
 * (-1 unlimited), the serials minted so far and the product info.
 */
public class CollectibleShopOffersComposer extends MessageComposer {
    private final List<Product> offers;

    public CollectibleShopOffersComposer(List<Product> offers) {
        this.offers = List.copyOf(offers);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleShopOffersComposer);
        this.response.appendInt(this.offers.size());
        for (Product offer : this.offers) {
            this.response.appendString(offer.productCode());
            this.response.appendInt(offer.emeraldPrice());
            this.response.appendBoolean(false);
            this.response.appendBoolean(!offer.canMintMore());
            this.response.appendInt(offer.mintLimit() < 0 ? -1 : offer.mintLimit());
            this.response.appendInt(offer.mintedCount());
            CollectibleWire.appendProduct(this.response, offer);
        }
        return this.response;
    }
}
