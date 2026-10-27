package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import org.junit.jupiter.api.Test;

class WiredSaveGateTest {
    @Test
    void moreThanTwentyPicksAreDroppedCountingRepeats() {
        WiredSettings settings = new WiredSettings(new int[0], "", new int[0], 0);
        settings.setRawPickCounts(21, 0);
        assertTrue(WiredSaveGate.dropsSilently(settings));

        settings.setRawPickCounts(20, 21);
        assertTrue(WiredSaveGate.dropsSilently(settings));

        settings.setRawPickCounts(20, 20);
        assertFalse(WiredSaveGate.dropsSilently(settings));
    }

    @Test
    void aRepeatedPickIsKeptOnce() {
        WiredSettings settings = new WiredSettings(new int[0], "", new int[] {5, 7, 5, 9, 7}, 0);
        settings.setFurniIds2(new int[] {3, 3});

        WiredSaveGate.keepPicksOnce(settings);

        assertArrayEquals(new int[] {5, 7, 9}, settings.getFurniIds());
        assertArrayEquals(new int[] {3}, settings.getFurniIds2());
    }

    @Test
    void onlyDelaysTheEditorCanSendAreSaved() {
        for (int delay : new int[] {0, 120, -50, -60_000, -1_050}) {
            assertTrue(WiredSaveGate.isEditorDelay(delay), String.valueOf(delay));
        }
        for (int delay : new int[] {121, -1, -75, -60_050}) {
            assertFalse(WiredSaveGate.isEditorDelay(delay), String.valueOf(delay));
        }

        WiredSettings settings = new WiredSettings(new int[0], "", new int[0], 0);
        assertNull(WiredSaveGate.delayRefusal(settings));
        settings.setRawDelay(121);
        assertEquals(WiredSaveGate.INVALID_DELAY, WiredSaveGate.delayRefusal(settings));
    }
}
