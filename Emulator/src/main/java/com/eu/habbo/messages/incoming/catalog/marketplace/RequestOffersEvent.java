package com.eu.habbo.messages.incoming.catalog.marketplace;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceOffer;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.catalog.marketplace.MarketplaceOffersComposer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class RequestOffersEvent extends MessageHandler {
    public static final OffersCache cachedResults = new OffersCache();

    /**
     * The default listings, kept for a short while. Entries never expired, so offers that had left the 48 hour window
     * stayed listed until the next sale, and a listing worked out before a sale could be stored after the sale had
     * cleared the cache. Entries now expire, and one computed before a clear is not stored.
     */
    public static final class OffersCache {
        static final long TTL_MS = 60_000;

        private record Entry(ServerMessage message, long storedAt) {}

        private final Map<Integer, Entry> entries = new ConcurrentHashMap<>();
        private final AtomicLong version = new AtomicLong();

        public ServerMessage get(int key, long now) {
            Entry entry = this.entries.get(key);

            if (entry == null) return null;

            if (now - entry.storedAt() > TTL_MS) {
                this.entries.remove(key, entry);
                return null;
            }

            return entry.message();
        }

        /** The version to pass to {@link #put}: read it before the listing is worked out. */
        public long version() {
            return this.version.get();
        }

        public void put(int key, ServerMessage message, long version, long now) {
            if (this.version.get() != version) return;

            this.entries.put(key, new Entry(message, now));

            // A clear that ran between the check and the put wins.
            if (this.version.get() != version) this.entries.remove(key);
        }

        public void clear() {
            this.version.incrementAndGet();
            this.entries.clear();
        }
    }

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
            ServerMessage message = cachedResults.get(cacheKey, System.currentTimeMillis());
            if (message != null) {
                this.client.sendResponse(message);
                return;
            }
        }

        long version = cachedResults.version();
        List<MarketPlaceOffer> offers = MarketPlace.getOffers(min, max, query, type, combineUniques);

        ServerMessage message = new MarketplaceOffersComposer(offers).compose();
        if (tryCache) {
            cachedResults.put(cacheKey, message, version, System.currentTimeMillis());
        }

        this.client.sendResponse(message);
    }

    /** The two groupings are different result sets, so they cannot share a cache slot. */
    private static int cacheKey(int sort, boolean combineUniques) {
        return (sort << 1) | (combineUniques ? 1 : 0);
    }
}
