package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Collecting what a sold offer earned is a claim, and it has to happen once. The
 * update carried only the offer id, so two sessions of the same seller could each
 * take the same sold offer and each be paid for it.
 */
class MarketplaceClaimOnceTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/marketplace/MarketPlace.java");

    @Test
    void theClaimIsKeyedOnTheSellerAndTheSoldState() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(
                source.contains("UPDATE marketplace_items SET user_id = -1 WHERE id = ? AND user_id = ? AND state = ?"),
                "the claim must name the seller and the state it is claiming from:\n" + source);
    }

    @Test
    void theStateItClaimsFromIsSold() {
        assertEquals(2, MarketPlaceState.SOLD.getState());
    }
}
