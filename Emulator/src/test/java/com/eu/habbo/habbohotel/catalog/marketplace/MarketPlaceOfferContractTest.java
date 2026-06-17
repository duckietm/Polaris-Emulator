package com.eu.habbo.habbohotel.catalog.marketplace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class MarketPlaceOfferContractTest {

    @Test
    void exposesPersistenceState() {
        assertDoesNotThrow(() -> MarketPlaceOffer.class.getDeclaredMethod("isPersisted"));
    }
}
