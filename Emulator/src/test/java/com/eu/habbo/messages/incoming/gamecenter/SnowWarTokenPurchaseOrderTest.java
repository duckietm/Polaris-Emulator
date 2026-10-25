package com.eu.habbo.messages.incoming.gamecenter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Buying SnowWar games credited the games first and charged afterwards through
 * giveCredits/givePoints, whose events a plugin can cancel or shrink. A cancelled or
 * failed debit left the games free. The price is now taken in one ledger mutation
 * before any game is credited, and given back when the credit fails.
 */
class SnowWarTokenPurchaseOrderTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/gamecenter/PurchaseSnowWarGameTokensOfferEvent.java");

    @Test
    void thePriceIsTakenBeforeTheGamesAreCredited() throws Exception {
        String source = Files.readString(SOURCE);

        int take = source.indexOf("CatalogPaymentService.tryTake(habbo, paidCredits, offer.pointsType(), paidPoints)");
        int credit = source.indexOf("offers.addExtraGames(");

        assertTrue(take > 0, "the price goes through the ledger:\n" + source);
        assertTrue(credit > take, "and before any game is credited");
    }

    @Test
    void aFailedCreditGivesThePriceBack() throws Exception {
        String source = Files.readString(SOURCE);

        int credit = source.indexOf("offers.addExtraGames(");
        int refund = source.indexOf("CatalogPaymentService.refund(habbo, paidCredits, offer.pointsType(), paidPoints)");

        assertTrue(refund > credit, "a purchase that credited nothing is refunded:\n" + source);
    }

    @Test
    void theCancellableWalletPathIsGone() throws Exception {
        String source = Files.readString(SOURCE);

        assertFalse(source.contains("habbo.giveCredits(-"), "credits are not taken through a cancellable event");
        assertFalse(source.contains("habbo.givePoints("), "nor are points");
    }

    @Test
    void repeatedPurchasesAreRateLimited() {
        assertTrue(new PurchaseSnowWarGameTokensOfferEvent().getRatelimit() > 0);
    }
}
