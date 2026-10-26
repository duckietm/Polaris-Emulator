package com.eu.habbo.messages.incoming.catalog;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * With the LTD raffle on, limited offers are collected and drawn rather than sold to
 * whoever clicks first. Buying one as a gift reserved a number directly, so a gift - to
 * an alt, or to oneself - skipped the draw and took a copy at once.
 */
class LtdGiftRaffleContractTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/catalog/CatalogBuyItemAsGiftEvent.java");

    @Test
    void aLimitedGiftIsRefusedWhileTheRaffleIsOn() throws Exception {
        String source = Files.readString(SOURCE);

        int guard = source.indexOf("item.isLimited() && LtdRaffleManager.isEnabled()");
        int reserve = source.indexOf("this.reserveLimitedNumber(item");

        assertTrue(guard > 0, "the gift path has to consult the raffle:\n" + source);
        assertTrue(reserve > guard, "and before a limited number is reserved");
    }
}
