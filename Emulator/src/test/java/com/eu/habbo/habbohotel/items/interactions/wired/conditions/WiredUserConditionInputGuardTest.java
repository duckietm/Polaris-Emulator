package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WiredUserConditionInputGuardTest {

    @Test
    void badgeCodesAreBoundedAndSingleLine() {
        assertEquals("", WiredUserConditionInputGuard.normalizeBadgeCode(null));
        assertEquals("ACH Test", WiredUserConditionInputGuard.normalizeBadgeCode(" ACH\tTest\n"));
        String normalized = WiredUserConditionInputGuard.normalizeBadgeCode("x".repeat(WiredUserConditionInputGuard.MAX_BADGE_CODE_LENGTH + 10));
        assertEquals(WiredUserConditionInputGuard.MAX_BADGE_CODE_LENGTH, normalized.length());
        assertFalse(normalized.contains("\n"));
    }

    @Test
    void numericIdsAreNonNegativeAndCapped() {
        assertEquals(0, WiredUserConditionInputGuard.normalizeEffectId(-1));
        assertEquals(WiredUserConditionInputGuard.MAX_EFFECT_ID, WiredUserConditionInputGuard.normalizeEffectId(Integer.MAX_VALUE));
        assertEquals(0, WiredUserConditionInputGuard.normalizeHandItemId(-1));
        assertEquals(WiredUserConditionInputGuard.MAX_HAND_ITEM_ID, WiredUserConditionInputGuard.normalizeHandItemId(Integer.MAX_VALUE));
    }

    @Test
    void unknownUserSourcesFallBackToTrigger() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredUserConditionInputGuard.normalizeUserSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, WiredUserConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SELECTOR));
    }
}
