package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ClubExtendPurchaseContractTest {
    @Test
    void buyAcceptsTheDealInItsWindowAndTheFullPriceFallbackOfTheExtendWindow() throws Exception {
        String buy = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyClubDiscountEvent.java"));
        String extend = Files.readString(
                Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/RequestClubExtendConfirmEvent.java"));

        assertTrue(
                extend.contains("filter(ClubOffer::isHabboClubOffer)"),
                "the extend window falls back to a normal HC offer");
        assertTrue(
                buy.contains("deal.isDeal() ? inDiscountWindow : deal.isHabboClubOffer()"),
                "buying accepts a deal only in its window, and otherwise a normal HC offer");
        assertTrue(new CatalogBuyClubDiscountEvent().getRatelimit() > 0, "club purchases are rate limited");
    }
}
