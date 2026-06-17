package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WiredMatchPositionInputGuardTest {

    @Test
    void furniSourcesFallBackToTriggerWhenUnknown() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredMatchPositionInputGuard.normalizeFurniSource(-1, false));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, WiredMatchPositionInputGuard.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTOR, false));
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, WiredMatchPositionInputGuard.normalizeFurniSource(WiredSourceUtil.SOURCE_SIGNAL, false));
    }

    @Test
    void selectedSettingsPromoteTriggerSourceToSelected() {
        assertEquals(WiredSourceUtil.SOURCE_SELECTED,
                WiredMatchPositionInputGuard.normalizeFurniSource(WiredSourceUtil.SOURCE_TRIGGER, true));
    }

    @Test
    void stateIsNullSafeSingleLineAndBounded() {
        assertEquals("", WiredMatchPositionInputGuard.normalizeState(null));
        assertEquals("a b c", WiredMatchPositionInputGuard.normalizeState("a\tb\nc"));
        String longState = "x".repeat(WiredMatchPositionInputGuard.MAX_STATE_LENGTH + 10);
        String normalized = WiredMatchPositionInputGuard.normalizeState(longState);
        assertEquals(WiredMatchPositionInputGuard.MAX_STATE_LENGTH, normalized.length());
        assertFalse(normalized.contains("\n"));
    }
}
