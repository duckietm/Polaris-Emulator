package polaris.hotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class MarketPlaceOfferContractTest {

    @Test
    void exposesPersistenceState() {
        assertDoesNotThrow(() -> MarketPlaceOffer.class.getDeclaredMethod("isPersisted"));
    }
}
