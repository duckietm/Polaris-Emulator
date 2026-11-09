package polaris.messages.outgoing.catalog.marketplace;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * NftMarketplaceMakeOfferResult (3524): the result of offering a collectible, with the codes of
 * {@link MarketplaceItemPostedComposer} (1 posted, 2 technical error, 3 marketplace disabled).
 */
public class NftMarketplaceMakeOfferResultComposer extends MessageComposer {
    private final int result;

    public NftMarketplaceMakeOfferResultComposer(int result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NftMarketplaceMakeOfferResultComposer);
        this.response.appendInt(this.result);
        return this.response;
    }
}
