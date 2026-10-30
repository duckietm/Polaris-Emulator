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
    /**
     * The default listings. The field keeps its type, a {@code Map}, because plugins compiled against it read and
     * clear it; the expiry and the version live in {@link OffersCache}, which is that map.
     */
    public static final Map<Integer, ServerMessage> cachedResults = new OffersCache();

    private static final OffersCache OFFERS = (OffersCache) cachedResults;

    /**
     * The default listings, kept for a short while. Entries never expired, so offers that had left the 48 hour window
     * stayed listed until the next sale, and a listing worked out before a sale could be stored after the sale had
     * cleared the cache. Entries now expire, and one computed before a clear is not stored. A plain {@code clear()},
     * from this emulator or a plugin, also moves the version on.
     */
    static final class OffersCache extends ConcurrentHashMap<Integer, ServerMessage> {
        static final long TTL_MS = 60_000;

        private final Map<Integer, Long> storedAt = new ConcurrentHashMap<>();
        private final AtomicLong version = new AtomicLong();

        ServerMessage fresh(int key, long now) {
            ServerMessage message = this.get(key);
            Long at = this.storedAt.get(key);

            if (message == null || at == null) return null;

            if (now - at > TTL_MS) {
                this.remove(key, message);
                this.storedAt.remove(key, at);
                return null;
            }

            return message;
        }

        /** The version to pass to {@link #store}: read it before the listing is worked out. */
        long version() {
            return this.version.get();
        }

        void store(int key, ServerMessage message, long version, long now) {
            if (this.version.get() != version) return;

            this.storedAt.put(key, now);
            this.put(key, message);

            // A clear that ran between the check and the put wins.
            if (this.version.get() != version) {
                this.remove(key);
                this.storedAt.remove(key);
            }
        }

        @Override
        public void clear() {
            this.version.incrementAndGet();
            this.storedAt.clear();
            super.clear();
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
            ServerMessage message = OFFERS.fresh(cacheKey, System.currentTimeMillis());
            if (message != null) {
                this.client.sendResponse(message);
                return;
            }
        }

        long version = OFFERS.version();
        List<MarketPlaceOffer> offers = MarketPlace.getOffers(min, max, query, type, combineUniques);

        ServerMessage message = new MarketplaceOffersComposer(offers).compose();
        if (tryCache) {
            OFFERS.store(cacheKey, message, version, System.currentTimeMillis());
        }

        this.client.sendResponse(message);
    }

    /** The two groupings are different result sets, so they cannot share a cache slot. */
    private static int cacheKey(int sort, boolean combineUniques) {
        return (sort << 1) | (combineUniques ? 1 : 0);
    }
}
