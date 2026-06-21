package com.eu.habbo.habbohotel.catalog.marketplace;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketPlaceListingPriceContractTest {

    @Test
    void listingPriceMustBePositiveAndBounded() {
        assertFalse(MarketPlace.isValidListingPrice(Integer.MIN_VALUE));
        assertFalse(MarketPlace.isValidListingPrice(-1));
        assertFalse(MarketPlace.isValidListingPrice(0));
        assertTrue(MarketPlace.isValidListingPrice(1));
        assertTrue(MarketPlace.isValidListingPrice(MarketPlace.MAXIMUM_LISTING_PRICE));
        assertFalse(MarketPlace.isValidListingPrice(MarketPlace.MAXIMUM_LISTING_PRICE + 1));
    }

    @Test
    void commissionCalculationDoesNotOverflow() {
        assertEquals(101, MarketPlace.calculateCommision(100));
        assertEquals(Integer.MAX_VALUE, MarketPlace.calculateCommision(Integer.MAX_VALUE));
    }

    @Test
    void marketplaceCoreRejectsInvalidListingPrices() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));
        int sellItemStart = source.indexOf("public static boolean sellItem");
        int eventCreation = source.indexOf("new MarketPlaceItemOfferedEvent", sellItemStart);
        int validation = source.indexOf("!isValidListingPrice(price)", sellItemStart);

        assertTrue(sellItemStart > -1, "MarketPlace.sellItem must exist");
        assertTrue(validation > sellItemStart, "Marketplace listings must validate price before persisting");
        assertTrue(validation < eventCreation, "Invalid prices must be rejected before plugin events and DB writes");
    }

    @Test
    void marketplaceBuyIgnoresInvalidPersistedPrices() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));
        int buyItemStart = source.indexOf("public static void buyItem");
        int rawPrice = source.indexOf("int rawPrice = set.getInt(\"price\")", buyItemStart);
        int validation = source.indexOf("!isValidListingPrice(rawPrice)", rawPrice);
        int commission = source.indexOf("MarketPlace.calculateCommision(rawPrice)", rawPrice);

        assertTrue(buyItemStart > -1, "MarketPlace.buyItem must exist");
        assertTrue(rawPrice > buyItemStart, "Marketplace buy path should read the persisted raw price");
        assertTrue(validation > rawPrice, "Persisted marketplace prices must be validated before charging buyers");
        assertTrue(validation < commission, "Invalid persisted prices must not reach commission calculation");
    }
}
