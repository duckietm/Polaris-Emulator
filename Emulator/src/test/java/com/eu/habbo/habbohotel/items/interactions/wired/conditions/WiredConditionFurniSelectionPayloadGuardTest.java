package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredConditionFurniSelectionPayloadGuardTest {
    @Test
    void furniHaveFurniBoundsFurniSources() {
        WiredConditionFurniHaveFurni condition = new WiredConditionFurniHaveFurni(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(-10));
    }

    @Test
    void furniHaveHabboBoundsFurniSources() {
        WiredConditionFurniHaveHabbo condition = new WiredConditionFurniHaveHabbo(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SIGNAL, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SIGNAL));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(8_000));
    }

    @Test
    void triggerOnFurniBoundsFurniUserSourcesAndQuantifier() {
        WiredConditionTriggerOnFurni condition = new WiredConditionTriggerOnFurni(1, 1, null, "", 0, 0);

        assertEquals(WiredSourceUtil.SOURCE_SELECTED, condition.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeFurniSource(77));
        assertEquals(WiredSourceUtil.SOURCE_CLICKED_USER, condition.normalizeUserSource(WiredSourceUtil.SOURCE_CLICKED_USER));
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, condition.normalizeUserSource(WiredSourceUtil.SOURCE_SELECTED));
        assertEquals(1, condition.normalizeQuantifier(1));
        assertEquals(0, condition.normalizeQuantifier(2));
    }
}
