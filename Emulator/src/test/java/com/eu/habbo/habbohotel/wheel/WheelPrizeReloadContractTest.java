package com.eu.habbo.habbohotel.wheel;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** A prize reload cleared and refilled the list a spin was picking from. */
class WheelPrizeReloadContractTest {

    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/wheel/WheelManager.java");

    @Test
    void aReloadSwapsInAWholeNewPrizeTable() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("private volatile PrizeTable prizeTable"));
        assertTrue(source.contains("this.prizeTable = new PrizeTable(Collections.unmodifiableList(loaded), weight);"));
        assertTrue(source.contains("PrizeTable table = this.prizeTable;"), "a spin picks from one snapshot");
        assertFalse(source.contains("this.prizes.clear()"));
    }
}
