package com.eu.habbo.habbohotel.wired;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WiredGiveRewardItemTest {
    @Test
    void parsesLegacyRewardPayload() {
        WiredGiveRewardItem item = new WiredGiveRewardItem("7,1,credits#25,40");

        assertEquals(7, item.id);
        assertEquals(false, item.badge);
        assertEquals("credits#25", item.data);
        assertEquals(40, item.probability);
    }

    @Test
    void rejectsMalformedRewardPayloads() {
        assertThrows(IllegalArgumentException.class, () -> new WiredGiveRewardItem("7,1,credits#25"));
        assertThrows(IllegalArgumentException.class, () -> new WiredGiveRewardItem("abc,1,credits#25,40"));
        assertThrows(IllegalArgumentException.class, () -> new WiredGiveRewardItem("7,1,credits#25,nope"));
    }

    @Test
    void clampsRewardProbability() {
        assertEquals(100, new WiredGiveRewardItem(1, false, "credits#25", 200).probability);
        assertEquals(0, new WiredGiveRewardItem(1, false, "credits#25", -20).probability);
        assertEquals(100, new WiredGiveRewardItem("1,1,credits#25,200").probability);
    }
}
