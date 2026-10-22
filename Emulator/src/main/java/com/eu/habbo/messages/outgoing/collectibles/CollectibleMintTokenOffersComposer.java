package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.MintTokenOffer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

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
