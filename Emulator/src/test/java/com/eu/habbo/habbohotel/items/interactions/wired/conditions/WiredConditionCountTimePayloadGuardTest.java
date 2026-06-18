package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionCountTimePayloadGuardTest {
    @Test
    void userCountLimitsAndSourcesAreBounded() {
        WiredConditionHabboCount condition = new WiredConditionHabboCount(1, 1, null, "", 0, 0);

        assertEquals(0, condition.normalizeLimit(-20));
        assertEquals(25, condition.normalizeLimit(25));
        assertEquals(WiredConditionHabboCount.MAX_USER_COUNT_LIMIT, condition.normalizeLimit(50_000));
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, condition.normalizeUserSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeUserSource(-55));
    }

    @Test
    void invertedUserCountRangesAreSorted() {
        WiredConditionHabboCount condition = new WiredConditionHabboCount(1, 1, null, "", 0, 0);

        condition.setLimits(80, 10);

        assertEquals("{\"lowerLimit\":10,\"upperLimit\":80,\"userSource\":0}", condition.getWiredData());
    }

    @Test
    void notUserCountUsesSameBounds() {
        WiredConditionNotHabboCount condition = new WiredConditionNotHabboCount(1, 1, null, "", 0, 0);

        assertEquals(0, condition.normalizeLimit(-1));
        assertEquals(WiredConditionHabboCount.MAX_USER_COUNT_LIMIT, condition.normalizeLimit(9_999));
        assertEquals(WiredSourceUtil.SOURCE_CLICKED_USER, condition.normalizeUserSource(WiredSourceUtil.SOURCE_CLICKED_USER));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeUserSource(777));
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
