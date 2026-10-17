package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Filter by variable sorted by value over a variable without a value keeps nobody; time sorts still work. */
class WiredExtraVariableFilterValueSortTest {

    @Test
    void aValueSortNeedsAValue() {
        assertFalse(WiredExtraVariableFilterBase.sortable(false, WiredExtraVariableFilterBase.SORT_VALUE_HIGHEST));
        assertFalse(WiredExtraVariableFilterBase.sortable(false, WiredExtraVariableFilterBase.SORT_VALUE_LOWEST));
        assertTrue(WiredExtraVariableFilterBase.sortable(false, WiredExtraVariableFilterBase.SORT_CREATION_OLDEST));
        assertTrue(WiredExtraVariableFilterBase.sortable(true, WiredExtraVariableFilterBase.SORT_VALUE_HIGHEST));
    }
}
