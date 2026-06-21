package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionCountTimePayloadGuardTest {
    @Test
    void userCountLimitsAndSourcesAreBounded() {
        assertEquals(0, WiredConditionInputGuard.normalizeUserCountRange(-20, -20)[0]);
        assertEquals(25, WiredConditionInputGuard.normalizeUserCountRange(25, 25)[0]);
        assertEquals(WiredConditionHabboCount.MAX_USER_COUNT_LIMIT,
            WiredConditionInputGuard.normalizeUserCountRange(50_000, 50_000)[0]);
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL,
            WiredConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredConditionInputGuard.normalizeUserSource(-55));
    }

    @Test
    void invertedUserCountRangesAreSorted() {
        int[] range = WiredConditionInputGuard.normalizeUserCountRange(80, 10);

        assertEquals(10, range[0]);
        assertEquals(80, range[1]);
    }

    @Test
    void notUserCountUsesSameBounds() {
        assertEquals(0, WiredConditionInputGuard.normalizeUserCountRange(-1, -1)[0]);
        assertEquals(WiredConditionHabboCount.MAX_USER_COUNT_LIMIT,
            WiredConditionInputGuard.normalizeUserCountRange(9_999, 9_999)[0]);
        assertEquals(WiredSourceUtil.SOURCE_CLICKED_USER,
            WiredConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_CLICKED_USER));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredConditionInputGuard.normalizeUserSource(777));
    }

    @Test
    void elapsedTimeCyclesAreBounded() {
        WiredConditionMoreTimeElapsed more = new WiredConditionMoreTimeElapsed(1, 1, null, "", 0, 0);
        WiredConditionLessTimeElapsed less = new WiredConditionLessTimeElapsed(1, 1, null, "", 0, 0);

        assertEquals(0, more.normalizeCycles(-1));
        assertEquals(42, more.normalizeCycles(42));
        assertEquals(WiredConditionMoreTimeElapsed.MAX_CYCLES, more.normalizeCycles(Integer.MAX_VALUE));
        assertEquals(0, less.normalizeCycles(-1));
        assertEquals(WiredConditionMoreTimeElapsed.MAX_CYCLES, less.normalizeCycles(Integer.MAX_VALUE));
    }
}
