package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceOffer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOffersComposer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RequestOffersEvent extends MessageHandler {
    public static final Map<Integer, ServerMessage> cachedResults = new ConcurrentHashMap<>(0);

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        int min = MarketplaceInputGuard.normalizeMinPrice(this.packet.readInt());
        int max = MarketplaceInputGuard.normalizeMaxPrice(this.packet.readInt(), min);
        String query = MarketplaceInputGuard.normalizeSearch(this.packet.readString());
        int type = MarketplaceInputGuard.normalizeSort(this.packet.readInt());

        // AIR 13 sends a fifth field, the `combine_uniques_checkbox` of
        // `marketplace_search_simple` (`HabboCatalog.getPublicMarketPlaceOffers`). Older
        // clients stop after the sort, so the flag is only read when it is really there
        // and otherwise keeps the historic behaviour: one row per unique serial.
        boolean combineUniques = this.packet.bytesAvailable() > 0 && this.packet.readBoolean();

        boolean tryCache = min == -1 && max == -1 && query.isEmpty();
        int cacheKey = cacheKey(type, combineUniques);

        if (tryCache) {
            ServerMessage message = cachedResults.get(cacheKey);
            if (message != null) {
                this.client.sendResponse(message);
                return;
            }
        }

        List<MarketPlaceOffer> offers = MarketPlace.getOffers(min, max, query, type, combineUniques);

        ServerMessage message = new MarketplaceOffersComposer(offers).compose();
        if (tryCache) {
            cachedResults.put(cacheKey, message);
        }

        this.client.sendResponse(message);
    }

    /** The two groupings are different result sets, so they cannot share a cache slot. */
    private static int cacheKey(int sort, boolean combineUniques) {
        return (sort << 1) | (combineUniques ? 1 : 0);
    }
}
