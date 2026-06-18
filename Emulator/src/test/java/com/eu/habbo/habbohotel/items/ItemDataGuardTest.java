package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemDataGuardTest {

    @Test
    void normalizesExtraDataToDatabaseBound() {
        assertEquals("", ItemDataGuard.normalizeExtraData(null));
        assertEquals(ItemDataGuard.MAX_EXTRA_DATA_LENGTH,
                ItemDataGuard.normalizeExtraData("x".repeat(ItemDataGuard.MAX_EXTRA_DATA_LENGTH + 1)).length());
    }

    @Test
    void parsesOnlyPositiveVendingIds() {
        assertArrayEquals(new int[]{1, 2, 3}, ItemDataGuard.parsePositiveIntList("1; 2.bad,3,-4,0"));
    }

    @Test
    void ignoresMalformedMultiHeights() {
        assertArrayEquals(new double[]{0.5, 1.25}, ItemDataGuard.parseHeights("0.5;nope;Infinity;1.25"));
    }
}
