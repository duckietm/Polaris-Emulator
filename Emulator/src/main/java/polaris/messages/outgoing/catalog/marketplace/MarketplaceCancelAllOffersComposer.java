package polaris.messages.outgoing.catalog.marketplace;

import java.util.List;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Answer to the AIR 13 "recall all" button (`MarketPlaceLogic.onCancelAllResult`, event
 * 1949): the ids of the offers that were actually pulled back, then whether the whole
 * batch went through. The client removes exactly those rows from its own-offers table.
 */
public class MarketplaceCancelAllOffersComposer extends MessageComposer {
    private final List<Integer> offerIds;
    private final boolean success;

    public MarketplaceCancelAllOffersComposer(List<Integer> offerIds, boolean success) {
        this.offerIds = offerIds == null ? List.of() : List.copyOf(offerIds);
        this.success = success;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MarketplaceCancelAllOffersComposer);
        this.response.appendInt(this.offerIds.size());

        for (Integer offerId : this.offerIds) {
            this.response.appendInt(offerId);
        }

        this.response.appendBoolean(this.success);
        return this.response;
    }

    public List<Integer> getOfferIds() {
        return this.offerIds;
    }

    public boolean isSuccess() {
        return this.success;
    }
}
