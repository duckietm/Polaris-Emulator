package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The LTD draw announced a winner by position - the first N entrants after the shuffle,
 * N being the copies left - whether or not their purchase went through. purchaseItem
 * reports nothing when the buyer cannot pay or their inventory is full, so that entrant
 * was told they won, nothing was sold, and the copy was lost to the draw.
 */
class LtdRaffleDrawContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/catalog/LtdRaffleManager.java");

    private static String draw() throws Exception {
        String source = Files.readString(SOURCE);
        int start = source.indexOf("private static void draw(");
        int end = source.indexOf("public static void cancelAll(", start);
        return source.substring(start, end);
    }

    @Test
    void aWinIsWhatTheLimitedPoolSays() throws Exception {
        String draw = draw();

        assertTrue(draw.contains("int before = configuration.available();"), draw);
        assertTrue(draw.contains("won = configuration.available() < before;"), "a win is a copy that left the pool");
        assertFalse(draw.contains("boolean won = index < copies;"), "position alone no longer decides a win");
    }

    @Test
    void aFailedPurchaseHandsTheCopyOn() throws Exception {
        String draw = draw();

        int purchase = draw.indexOf("catalogManager.purchaseItem(");
        int decrement = draw.indexOf("if (won) copies--;");

        assertTrue(purchase > 0 && decrement > purchase, "a copy is only counted as gone once it is sold:\n" + draw);
    }
}
