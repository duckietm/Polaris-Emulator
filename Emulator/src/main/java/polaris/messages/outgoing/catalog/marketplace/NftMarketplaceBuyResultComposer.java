package polaris.messages.outgoing.catalog.marketplace;

import polaris.hotel.catalog.marketplace.NftMarketplace;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * NftMarketplaceBuyResult (1583): the answer to buying a collectible. AIR reads the result ({@code SUCCESS},
 * {@code NOT_ENOUGH_EMERALDS} or anything else as a failure), the offer id as a 64-bit integer and the price.
 */
public class NftMarketplaceBuyResultComposer extends MessageComposer {
    private final NftMarketplace.BuyResult result;

    public NftMarketplaceBuyResultComposer(NftMarketplace.BuyResult result) {
        this.result = result;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NftMarketplaceBuyResultComposer);
        this.response.appendString(this.result.result());
        // A 64-bit integer: the high int, then the low one.
        this.response.appendInt(0);
        this.response.appendInt(this.result.offerId());
        this.response.appendInt(this.result.price());
        return this.response;
    }
}
