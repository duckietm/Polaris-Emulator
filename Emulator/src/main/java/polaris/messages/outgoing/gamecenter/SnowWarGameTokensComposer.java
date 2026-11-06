package polaris.messages.outgoing.gamecenter;

import java.util.List;
import polaris.hotel.games.snowwar.SnowWarTokenOfferRepository;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR SnowWarGameTokensMessageEvent (3419): the "get more games" offers, keyed
 * by their localization id (GET_SNOWWAR_TOKENS, ..2, ..3).
 */
public class SnowWarGameTokensComposer extends MessageComposer {

    private final List<SnowWarTokenOfferRepository.Offer> offers;

    public SnowWarGameTokensComposer(List<SnowWarTokenOfferRepository.Offer> offers) {
        this.offers = offers;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowWarGameTokensComposer);
        this.response.appendInt(this.offers.size());
        for (SnowWarTokenOfferRepository.Offer offer : this.offers) {
            this.response.appendInt(offer.offerId());
            this.response.appendString(offer.localizationId());
            this.response.appendInt(offer.priceInCredits());
            this.response.appendInt(offer.priceInPoints());
            this.response.appendInt(offer.pointsType());
        }
        return this.response;
    }
}
