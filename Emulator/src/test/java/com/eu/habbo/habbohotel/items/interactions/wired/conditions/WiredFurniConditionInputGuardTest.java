package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class WiredFurniConditionInputGuardTest {

    @Test
    void furniSourcesFallBackToTriggerWhenUnknown() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredFurniConditionInputGuard.normalizeFurniSource(-1));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR, WiredFurniConditionInputGuard.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(WiredSourceUtil.SOURCE_SELECTED, WiredFurniConditionInputGuard.normalizeFurniSource(WiredSourceUtil.SOURCE_SELECTED));
    }

    @Test
    void selectedItemsPromoteTriggerSourceToSelected() {
        assertEquals(WiredSourceUtil.SOURCE_SELECTED,
                WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(WiredSourceUtil.SOURCE_TRIGGER, true));
        assertEquals(WiredSourceUtil.SOURCE_SELECTOR,
                WiredFurniConditionInputGuard.selectedOrNormalizedFurniSource(WiredSourceUtil.SOURCE_SELECTOR, true));
    }

    @Test
    void itemIdsIgnoreInvalidValuesAndRespectCap() {
        assertIterableEquals(Arrays.asList(4, 9),
                WiredFurniConditionInputGuard.sanitizeItemIds(Arrays.asList(-1, 4, null, 9, 10), 2));
    }

    @Test
    void legacyItemIdsIgnoreMalformedParts() {
        assertIterableEquals(Arrays.asList(10, 20, 30),
                WiredFurniConditionInputGuard.parseLegacyItemIds("10;bad;-1;20\t30", 5));
        assertIterableEquals(Arrays.asList(10, 20),
                WiredFurniConditionInputGuard.parseLegacyItemIds("10;20;30", 2));
    }
}
