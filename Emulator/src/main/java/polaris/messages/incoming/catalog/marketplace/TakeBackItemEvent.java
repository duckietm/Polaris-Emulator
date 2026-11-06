package polaris.messages.incoming.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.messages.incoming.MessageHandler;

public class TakeBackItemEvent extends MessageHandler {
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
        int offerId = this.packet.readInt();

        if (!MarketplaceInputGuard.isPositiveId(offerId)) {
            return;
        }

        MarketPlace.takeBackItem(this.client.getUser(), offerId);
    }
}
