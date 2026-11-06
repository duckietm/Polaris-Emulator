package polaris.messages.outgoing.catalog.marketplace;

import java.util.List;
import polaris.hotel.catalog.marketplace.MarketPlace;
import polaris.hotel.catalog.marketplace.MarketPlaceOffer;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MarketplaceOffersComposer extends MessageComposer {
    private final List<MarketPlaceOffer> offers;

    public MarketplaceOffersComposer(List<MarketPlaceOffer> offers) {
        this.offers = offers;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceOffersComposer);
        this.response.appendInt(this.offers.size());

        for (MarketPlaceOffer offer : this.offers) {
            this.response.appendInt(offer.getOfferId());
            this.response.appendInt(1);
            this.response.appendInt(offer.getType());
            this.response.appendInt(offer.getItemId());
            if (offer.getType() == 3) {
                this.response.appendInt(offer.getLimitedNumber());
                this.response.appendInt(offer.getLimitedStack());
            } else if (offer.getType() == 2) {
                this.response.appendString("");
            } else {
                this.response.appendInt(0);
                this.response.appendString("");
            }
            this.response.appendInt(MarketPlace.calculateCommision(offer.getPrice()));
            this.response.appendInt(0);
            this.response.appendInt(MarketPlace.calculateCommision(offer.avarage));
            this.response.appendInt(offer.count);
        }
        this.response.appendInt(this.offers.size());
        return this.response;
    }

    public List<MarketPlaceOffer> getOffers() {
        return offers;
    }
}
