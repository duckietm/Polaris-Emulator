package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The HAS_ALTITUDE dialog offers "all of the furni match" and "any of the furni match"; the two height
 * conditions stored the choice and always behaved as "all". These cover the two readings and, above
 * all, that the boxes stay exact complements of each other under either one.
 */
class WiredConditionSameHeightQuantifierTest {

    private static HabboItem at(double z) {
        HabboItem item = mock(HabboItem.class);
        when(item.getZ()).thenReturn(z);
        return item;
    }

    private static List<HabboItem> heights(double... values) {
        HabboItem[] items = new HabboItem[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = at(values[i]);
        }
        return Arrays.asList(items);
    }

    @Test
    @DisplayName("all: every target has to sit at the same height")
    void allWantsOneHeight() {
        assertTrue(WiredConditionSameHeight.allShareHeight(heights(1.0, 1.0, 1.0)));
        assertFalse(WiredConditionSameHeight.allShareHeight(heights(1.0, 1.0, 2.0)));
    }

    @Test
    @DisplayName("any: two targets meeting is enough")
    void anyWantsOnePair() {
        assertTrue(WiredConditionSameHeight.anyPairShareHeight(heights(1.0, 2.0, 2.0)));
        assertFalse(WiredConditionSameHeight.anyPairShareHeight(heights(1.0, 2.0, 3.0)));
    }

    @Test
    @DisplayName("a lone target satisfies all and never satisfies any")
    void aSingleTarget() {
        assertTrue(WiredConditionSameHeight.allShareHeight(heights(1.0)));
        assertFalse(WiredConditionSameHeight.anyPairShareHeight(heights(1.0)));
        assertTrue(WiredConditionSameHeight.allShareHeight(Collections.emptyList()));
    }

    @Test
    @DisplayName("heights that differ only in their decimals are different heights")
    void decimalsCount() {
        assertFalse(WiredConditionSameHeight.allShareHeight(heights(1.0, 1.0001)));
        assertFalse(WiredConditionSameHeight.anyPairShareHeight(heights(1.0, 1.0001)));
    }

    @Test
    @DisplayName("a null target is skipped, not a failure")
    void nullTargetsAreSkipped() {
        assertTrue(WiredConditionSameHeight.allShareHeight(Arrays.asList(null, at(1.0), at(1.0))));
        assertTrue(WiredConditionSameHeight.anyPairShareHeight(Arrays.asList(null, at(1.0), at(1.0))));
    }

    @Test
    @DisplayName("all keeps its historical answer: equal heights pass, one odd one out fails")
    void allMatchesWhatTheBoxAlwaysDid() {
        assertTrue(WiredConditionSameHeight.allShareHeight(heights(2.5, 2.5, 2.5, 2.5)));
        assertFalse(WiredConditionSameHeight.allShareHeight(heights(2.5, 2.5, 2.5, 0.0)));
    }

    @Test
    @DisplayName("what the not-same-height box answers, which is this negated")
    void theComplementSpelledOut() {
        // all: "not every target at one height" - the reading that box has always had
        assertFalse(!WiredConditionSameHeight.allShareHeight(heights(1.0, 1.0)));
        assertTrue(!WiredConditionSameHeight.allShareHeight(heights(1.0, 2.0)));
        assertTrue(!WiredConditionSameHeight.allShareHeight(heights(1.0, 2.0, 2.0)));

        // any: "no two targets meet", i.e. every height is distinct
        assertTrue(!WiredConditionSameHeight.anyPairShareHeight(heights(1.0, 2.0, 3.0)));
        assertFalse(!WiredConditionSameHeight.anyPairShareHeight(heights(1.0, 2.0, 2.0)));
    }
}
