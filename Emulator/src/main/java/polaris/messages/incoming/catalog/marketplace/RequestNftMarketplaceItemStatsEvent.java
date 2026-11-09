package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.NftMarketplace;
import polaris.hotel.catalog.marketplace.NftMarketplaceSettings;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.NftMarketplaceItemStatsComposer;

/**
 * GetNftMarketplaceItemStats (2706): the prices of a collectible for AIR's make-offer and offer-details windows,
 * asked by product code.
 */
public class RequestNftMarketplaceItemStatsEvent extends MessageHandler {
    private static final int MAX_PRODUCT_CODE_LENGTH = 128;

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String productCode = this.packet.readString();

        if (!NftMarketplaceSettings.isProduct(productCode) || productCode.length() > MAX_PRODUCT_CODE_LENGTH) return;
        if (!NftMarketplaceSettings.current().active()) return;

        this.client.sendResponse(new NftMarketplaceItemStatsComposer(NftMarketplace.itemStats(productCode)));
    }
}
