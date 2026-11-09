package polaris.messages.outgoing.catalog.marketplace;

import polaris.hotel.catalog.marketplace.NftMarketplace;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * NftMarketplaceItemStats (2492): a collectible's prices in emeralds. AIR reads the average price, the open offers,
 * the history length, the history days (offset, average, sold), the lowest open price and the suggested price.
 */
public class NftMarketplaceItemStatsComposer extends MessageComposer {
    private final NftMarketplace.ItemStats stats;

    public NftMarketplaceItemStatsComposer(NftMarketplace.ItemStats stats) {
        this.stats = stats;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.NftMarketplaceItemStatsComposer);
        this.response.appendInt(this.stats.averagePrice());
        this.response.appendInt(this.stats.offerCount());
        this.response.appendInt(this.stats.historyLength());
        this.response.appendInt(this.stats.history().size());
        for (NftMarketplace.HistoryDay day : this.stats.history()) {
            this.response.appendInt(day.dayOffset());
            this.response.appendInt(day.averagePrice());
            this.response.appendInt(day.sold());
        }
        this.response.appendInt(this.stats.lowestCurrentPrice());
        this.response.appendInt(this.stats.suggestedPrice());
        return this.response;
    }
}
