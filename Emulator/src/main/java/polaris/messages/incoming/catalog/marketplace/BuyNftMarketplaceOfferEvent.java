package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.NftMarketplace;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.NftMarketplaceBuyResultComposer;

/**
 * BuyNftMarketplaceOffer (9425): AIR's {@code HabboCatalog.buyNftMarketPlaceOffer} buys a collectible by its product
 * code (the furni's class name) at the price the buyer saw: the cheapest open offer of the product is taken when it
 * costs no more than that.
 */
public class BuyNftMarketplaceOfferEvent extends MessageHandler {
    private static final int MAX_PRODUCT_CODE_LENGTH = 128;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public String getRatelimitGroup() {
        return "marketplace.mutation";
    }

    @Override
    public void handle() throws Exception {
        String productCode = this.packet.readString();
        int price = this.packet.readInt();

        if (productCode == null || productCode.isEmpty() || productCode.length() > MAX_PRODUCT_CODE_LENGTH) return;

        this.client.sendResponse(
                new NftMarketplaceBuyResultComposer(NftMarketplace.buy(this.client, productCode, price)));
    }
}
