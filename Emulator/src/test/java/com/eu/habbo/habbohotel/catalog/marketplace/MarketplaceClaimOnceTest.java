package com.eu.habbo.habbohotel.catalog.marketplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
                source.contains(
                        "UPDATE marketplace_items SET seller_claimed = 1 WHERE id = ? AND user_id = ? AND state = ? AND seller_claimed = 0"),
                "the claim must name the seller, the state it is claiming from, and an offer not paid out yet:\n"
                        + source);
    }

    /**
     * A database with the fk_market_user foreign key refuses user_id -1, so a claim that detached the offer
     * that way failed and the seller was never paid. The offer keeps its seller and is flagged paid out.
     */
    @Test
    void theClaimKeepsTheSellerSoTheForeignKeyHolds() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("SET user_id = -1"), "a claim must not detach the seller:\n" + source);
        assertTrue(
                source.contains("WHERE marketplace_items.user_id = ? AND marketplace_items.seller_claimed = 0"),
                "paid-out offers must not come back as the seller's own offers");
    }

    @Test
    void aMigrationAddsTheFlag() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20261028090000__marketplace_seller_claimed.sql"));

        assertTrue(
                migration.contains(
                        "ALTER TABLE `marketplace_items` ADD COLUMN IF NOT EXISTS `seller_claimed` TINYINT(1) NOT NULL DEFAULT 0"));
    }

    @Test
    void theStateItClaimsFromIsSold() {
        assertEquals(2, MarketPlaceState.SOLD.getState());
    }

    @Test
    void theIncomeWindowOnlyReportsAPayoutThatWentThrough() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/earnings/EarningsCenterManager.java"));

        assertTrue(source.contains("return MarketPlace.claimSoldCredits(habbo.getClient()) > 0;"));
    }
}
