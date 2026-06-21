package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionFurniPayloadGuardTest {
    @Test
    void counterTimeNormalizesTimeComparisonQuantifierAndSources() {
        WiredConditionCounterTimeMatches condition = new WiredConditionCounterTimeMatches(1, 1, null, "", 0, 0);

        assertEquals(1, condition.normalizeComparison(1));
        assertEquals(1, condition.normalizeComparison(-1));
        assertEquals(1, condition.normalizeComparison(99));
        assertEquals(0, condition.normalizeMinutes(-10));
        assertEquals(99, condition.normalizeMinutes(250));
        assertEquals(0, condition.normalizeHalfSecondSteps(-1));
        assertEquals(119, condition.normalizeHalfSecondSteps(500));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(7));
        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(42_424));
    }

    @Test
    void altitudeNormalizesPayloadInputs() {
        WiredConditionHasAltitude condition = new WiredConditionHasAltitude(1, 1, null, "", 0, 0);

        assertEquals(1, condition.normalizeComparison(1));
        assertEquals(1, condition.normalizeComparison(-5));
        assertEquals(1, condition.normalizeComparison(9));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(8));
        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(-900));
        assertEquals(0.0D, condition.parseAltitudeOrDefault("nope"));
        assertEquals(12.35D, condition.parseAltitudeOrDefault("12.345"));
        assertEquals("12.35", condition.formatAltitude(12.345D));
    }
}
