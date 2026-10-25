package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Buying took an offer id and checked the state and the price. It did not look at
 * whether the marketplace is on, whether the offer is past its listing window, or who
 * is selling - so an expired offer could still be bought by id, and a seller could buy
 * their own and move credits to themselves minus the commission.
 */
class MarketplaceBuyGuardsTest {

    private static String source(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    /** The regex class, not {@code "\s"} - which since Java 15 is an escape for one space. */
    private static String flatten(String source) {
        return source.replaceAll("\\s+", " ");
    }

    @Test
    void theOfferMustStillBeInsideItsListingWindow() throws Exception {
        String source = source("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java");

        assertTrue(
                source.contains("SELECT item_id, user_id, price, state, timestamp"),
                "the purchase has to read the timestamp it judges expiry by");
        assertTrue(
                source.contains("MarketplaceOffersQuery.LISTING_WINDOW_SECONDS"),
                "expiry uses the same window the listing does:\n" + source);
    }

    @Test
    void aSellerCannotBuyTheirOwnOffer() throws Exception {
        // Line breaks are the formatter's business, so the comparison is read without them.
        String source = flatten(source("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java"));

        assertTrue(source.contains("boolean ownOffer"), source);
        assertTrue(
                source.contains("set.getInt(\"user_id\") == client.getHabbo().getHabboInfo().getId()"),
                "the buyer is compared with the seller: " + source);
    }

    @Test
    void buyingIsRefusedWhileTheMarketplaceIsOff() throws Exception {
        String source = source("src/main/java/com/eu/habbo/messages/incoming/catalog/marketplace/BuyItemEvent.java");

        assertTrue(source.contains("MarketPlace.MARKETPLACE_ENABLED"), "buying has to honour the switch too");

        int gate = source.indexOf("MARKETPLACE_ENABLED");
        int buy = source.indexOf("MarketPlace.buyItem(");

        assertTrue(gate > 0 && buy > 0, source);
        assertTrue(gate < buy, "the switch is checked before the purchase runs");
    }

    @Test
    void theListingWindowIsTwoDays() {
        assertEquals(172800, MarketplaceOffersQuery.LISTING_WINDOW_SECONDS);
    }
}
