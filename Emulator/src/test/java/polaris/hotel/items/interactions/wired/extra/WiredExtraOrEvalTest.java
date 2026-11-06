package polaris.hotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * "Less than N" can only ever be satisfied when N is at least 1: no count is below zero. The other
 * counting modes keep 0 as a meaningful value ("exactly none", "more than none").
 */
class WiredExtraOrEvalTest {

    @Test
    void lessThanZeroIsReadAsLessThanOne() {
        assertTrue(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_LESS_THAN, 0, 3, 0));
        assertFalse(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_LESS_THAN, 1, 3, 0));
    }

    @Test
    void zeroStaysMeaningfulForTheOtherCountingModes() {
        assertTrue(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_EXACTLY, 0, 3, 0));
        assertFalse(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_EXACTLY, 1, 3, 0));
        assertTrue(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_MORE_THAN, 1, 3, 0));
        assertFalse(WiredExtraOrEval.matchesMode(WiredExtraOrEval.MODE_MORE_THAN, 0, 3, 0));
    }

    @Test
    void savingLessThanWithZeroStoresOne() throws Exception {
        WiredExtraOrEval extra = new WiredExtraOrEval(1, 1, mock(Item.class), "", 0, 0);

        extra.saveData(
                new WiredSettings(
                        new int[] {WiredExtraOrEval.MODE_LESS_THAN, WiredSourceUtil.SOURCE_TRIGGER, 0},
                        "",
                        new int[0],
                        0),
                null);
        assertEquals(1, extra.getCompareValue());

        extra.saveData(
                new WiredSettings(
                        new int[] {WiredExtraOrEval.MODE_EXACTLY, WiredSourceUtil.SOURCE_TRIGGER, 0},
                        "",
                        new int[0],
                        0),
                null);
        assertEquals(0, extra.getCompareValue());
    }
}
