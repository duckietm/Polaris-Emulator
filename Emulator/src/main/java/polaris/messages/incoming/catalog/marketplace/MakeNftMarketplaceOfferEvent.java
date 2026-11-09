package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.NftMarketplace;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.marketplace.NftMarketplaceMakeOfferResultComposer;

/**
 * MakeNftMarketplaceOffer (2346): AIR's {@code MarketplaceModel.makeNftOffer} offers one collectible for emeralds:
 * the furni kind (1 floor, 2 wall), the inventory item and the asking price.
 */
public class MakeNftMarketplaceOfferEvent extends MessageHandler {
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
        int furniType = this.packet.readInt();
        int itemId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        int price = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(itemId)) return;

        this.client.sendResponse(new NftMarketplaceMakeOfferResultComposer(
                NftMarketplace.makeOffer(this.client, furniType, itemId, price)));
    }
}
