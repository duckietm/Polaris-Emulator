package polaris.messages.outgoing.collectibles;

import java.util.List;
import polaris.hotel.collectibles.CollectibleData.MintTokenOffer;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/** AIR 15 stamp offers (745): offer id, product code, silver price and stamps in the pack. */
public class CollectibleMintTokenOffersComposer extends MessageComposer {
    private final List<MintTokenOffer> offers;

    public CollectibleMintTokenOffersComposer(List<MintTokenOffer> offers) {
        this.offers = List.copyOf(offers);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleMintTokenOffersComposer);
        this.response.appendInt(this.offers.size());
        for (MintTokenOffer offer : this.offers) {
            this.response.appendInt(offer.offerId());
            this.response.appendString(offer.productCode());
            this.response.appendInt(offer.silverPrice());
            this.response.appendInt(offer.amount());
        }
        return this.response;
    }
}
