package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** User Level (44) stores AIR's comparison (0 <, 1 =, 2 >); six-way boxes saved before convert to the same test. */
class WiredConditionUserLevelOfficialTest {

    private static WiredConditionUserLevel load(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        WiredConditionUserLevel box = new WiredConditionUserLevel(1, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        box.loadWiredData(set, WiredConditionTestSupport.room(1));
        return box;
    }

    private static String legacy(int level, int comparison) {
        return "{\"level\":" + level + ",\"comparison\":" + comparison
                + ",\"userSource\":0,\"quantifier\":0,\"variableToken\":\"\",\"variableItemId\":0}";
    }

    /** Whether the box passes for every level in 1..40 exactly when {@code expected} does. */
    private static void assertSameTest(WiredConditionUserLevel box, java.util.function.IntPredicate expected) {
        for (int reached = 1; reached <= 40; reached++) {
            assertEquals(expected.test(reached), box.matches(reached), "level " + reached);
        }
    }

    @Test
    void theOfficialComparisonIsSavedSentAndKept() throws Exception {
        WiredConditionUserLevel box = new WiredConditionUserLevel(1, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        box.saveData(WiredConditionTestSupport.settings(new int[] {12, WiredConditionUserLevel.OFFICIAL_LESS_THAN}));
        Room room = WiredConditionTestSupport.room(1);
        assertArrayEquals(new int[] {12, 0}, WiredLegacyWireReader.read(box, room).intParams);
        assertSameTest(box, reached -> reached < 12);

        WiredConditionUserLevel reopened = load(box.getWiredData());
        assertArrayEquals(new int[] {12, 0}, WiredLegacyWireReader.read(reopened, room).intParams);
        assertSameTest(reopened, reached -> reached < 12);

        // Anything that is not one of the three reads as "equal to".
        box.saveData(WiredConditionTestSupport.settings(new int[] {12, 5}));
        assertEquals(WiredConditionUserLevel.OFFICIAL_EQUAL, box.getComparison());
    }

    @Test
    void sixWayBoxesConvertToTheSameTest() throws Exception {
        assertSameTest(load(legacy(10, WiredConditionUserLevel.COMPARISON_GREATER_THAN)), reached -> reached > 10);
        assertSameTest(
                load(legacy(10, WiredConditionUserLevel.COMPARISON_GREATER_THAN_OR_EQUAL)), reached -> reached >= 10);
        assertSameTest(load(legacy(10, WiredConditionUserLevel.COMPARISON_EQUAL)), reached -> reached == 10);
        assertSameTest(
                load(legacy(10, WiredConditionUserLevel.COMPARISON_LESS_THAN_OR_EQUAL)), reached -> reached <= 10);
        assertSameTest(load(legacy(10, WiredConditionUserLevel.COMPARISON_LESS_THAN)), reached -> reached < 10);
        assertSameTest(load(legacy(1, WiredConditionUserLevel.COMPARISON_GREATER_THAN_OR_EQUAL)), reached -> true);

        Room room = WiredConditionTestSupport.room(1);
        // ">= 10" opens as "> 9", "<= 10" as "< 11".
        assertArrayEquals(
                new int[] {9, WiredConditionUserLevel.OFFICIAL_GREATER_THAN},
                WiredLegacyWireReader.read(load(legacy(10, 1)), room).intParams);
        assertArrayEquals(
                new int[] {11, WiredConditionUserLevel.OFFICIAL_LESS_THAN},
                WiredLegacyWireReader.read(load(legacy(10, 3)), room).intParams);
    }

    @Test
    void aSavedNotEqualKeepsItsTestUntilItIsSavedAgain() throws Exception {
        WiredConditionUserLevel box = load(legacy(10, WiredConditionUserLevel.COMPARISON_NOT_EQUAL));
        assertSameTest(box, reached -> reached != 10);
        // Stored again as it was, so a reload still tests "not equal".
        assertSameTest(load(box.getWiredData()), reached -> reached != 10);

        box.saveData(WiredConditionTestSupport.settings(new int[] {10, WiredConditionUserLevel.OFFICIAL_EQUAL}));
        assertTrue(box.matches(10));
        assertFalse(box.matches(11));
    }
}
