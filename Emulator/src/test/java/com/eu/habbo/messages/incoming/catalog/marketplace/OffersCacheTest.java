package com.eu.habbo.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import com.eu.habbo.messages.ServerMessage;
import org.junit.jupiter.api.Test;

class OffersCacheTest {

    @Test
    void aListingExpires() {
        RequestOffersEvent.OffersCache cache = new RequestOffersEvent.OffersCache();
        ServerMessage message = mock(ServerMessage.class);

        cache.put(1, message, cache.version(), 1_000);

        assertSame(message, cache.get(1, 1_000 + RequestOffersEvent.OffersCache.TTL_MS));
        assertNull(cache.get(1, 1_001 + RequestOffersEvent.OffersCache.TTL_MS));
    }

    @Test
    void aListingWorkedOutBeforeAClearIsNotStored() {
        RequestOffersEvent.OffersCache cache = new RequestOffersEvent.OffersCache();
        long version = cache.version();

        cache.clear();
        cache.put(1, mock(ServerMessage.class), version, 1_000);

        assertNull(cache.get(1, 1_000));
    }
}
