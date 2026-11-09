package polaris.messages.incoming.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Every sort, grouping and offer source is its own cached result set. */
class RequestOffersCacheKeyTest {

    @Test
    void neverSharesASlot() {
        Set<Integer> keys = new HashSet<>();
        int combinations = 0;
        for (int sort = MarketplaceInputGuard.MIN_SORT; sort <= MarketplaceInputGuard.MAX_SORT; sort++) {
            for (boolean combine : new boolean[] {false, true}) {
                for (int source = 0; source <= 2; source++) {
                    keys.add(RequestOffersEvent.cacheKey(sort, combine, source));
                    combinations++;
                }
            }
        }
        assertEquals(combinations, keys.size());
    }

    @Test
    void anUnknownOfferSourceIsEveryOffer() {
        assertEquals(MarketplaceInputGuard.OFFER_SOURCE_ALL, MarketplaceInputGuard.normalizeOfferSource(7));
        assertEquals(MarketplaceInputGuard.OFFER_SOURCE_COLLECTIBLES, MarketplaceInputGuard.normalizeOfferSource(1));
        assertEquals(MarketplaceInputGuard.OFFER_SOURCE_FURNI, MarketplaceInputGuard.normalizeOfferSource(2));
    }
}
