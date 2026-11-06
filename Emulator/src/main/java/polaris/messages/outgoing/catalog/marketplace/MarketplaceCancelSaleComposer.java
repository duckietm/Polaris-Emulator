package polaris.messages.outgoing.catalog.marketplace;

import polaris.hotel.catalog.marketplace.MarketPlaceOffer;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MarketplaceCancelSaleComposer extends MessageComposer {
    private final MarketPlaceOffer offer;
    private final boolean success;

    public MarketplaceCancelSaleComposer(MarketPlaceOffer offer, Boolean success) {
        this.offer = offer;
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceCancelSaleComposer);
        this.response.appendInt(this.offer.getOfferId());
        this.response.appendBoolean(this.success);
        return this.response;
    }

    public MarketPlaceOffer getOffer() {
        return offer;
    }

    public boolean isSuccess() {
        return success;
    }
}
