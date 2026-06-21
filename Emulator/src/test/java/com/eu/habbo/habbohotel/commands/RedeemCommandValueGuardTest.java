package com.eu.habbo.habbohotel.commands;

import gnu.trove.map.hash.TIntIntHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedeemCommandValueGuardTest {
    @Test
    void parsesOnlyPositiveRedeemValues() {
        assertEquals(50, RedeemCommand.parsePositiveRedeemValue("CF_50", 1));
        assertEquals(7, RedeemCommand.parsePositiveRedeemValue("DF_5_7", 2));
        assertNull(RedeemCommand.parsePositiveRedeemValue("CF_0", 1));
        assertNull(RedeemCommand.parsePositiveRedeemValue("CF_-10", 1));
        assertNull(RedeemCommand.parsePositiveRedeemValue("CF_nope", 1));
        assertNull(RedeemCommand.parsePositiveRedeemValue("CF_10", 2));
    }

    @Test
    void rejectsOverflowingCurrencyTotals() {
        assertEquals(15, RedeemCommand.addRedeemValue(10, 5));
        assertNull(RedeemCommand.addRedeemValue(Integer.MAX_VALUE, 1));
    }

    @Test
    void rejectsOverflowingPointTotalsWithoutChangingTheMap() {
        TIntIntHashMap points = new TIntIntHashMap();
        points.put(5, Integer.MAX_VALUE);

        assertFalse(RedeemCommand.addRedeemPoints(points, 5, 1));
        assertEquals(Integer.MAX_VALUE, points.get(5));

        assertTrue(RedeemCommand.addRedeemPoints(points, 6, 10));
        assertEquals(10, points.get(6));
    }
}
