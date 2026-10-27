package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import org.junit.jupiter.api.Test;

class WiredActionMemoryTest {
    @Test
    void theSelectorRemembersAWaveFiveSecondsAndARespectTwoAndAHalf() {
        RoomUnit unit = new RoomUnit();
        WiredActionMemory.record(unit, WiredUserActionType.WAVE, -1, 1_000L);
        assertArrayEquals(new int[] {WiredUserActionType.WAVE, -1}, WiredActionMemory.selectorRecent(unit, 5_900L));
        assertNull(WiredActionMemory.selectorRecent(unit, 6_100L));

        WiredActionMemory.record(unit, WiredUserActionType.RESPECT, -1, 10_000L);
        assertArrayEquals(new int[] {WiredUserActionType.RESPECT, -1}, WiredActionMemory.selectorRecent(unit, 12_400L));
        assertNull(WiredActionMemory.selectorRecent(unit, 12_600L));
    }

    @Test
    void aLaughOrADanceLeavesTheEarlierActionInPlace() {
        RoomUnit unit = new RoomUnit();
        WiredActionMemory.record(unit, WiredUserActionType.WAVE, -1, 1_000L);
        WiredActionMemory.record(unit, WiredUserActionType.LAUGH, -1, 2_000L);
        WiredActionMemory.record(unit, WiredUserActionType.DANCE, 2, 2_500L);

        assertArrayEquals(new int[] {WiredUserActionType.WAVE, -1}, WiredActionMemory.selectorRecent(unit, 4_000L));
    }

    @Test
    void anExpressionLastsTwoSecondsAndASitDoesNotHideIt() {
        RoomUnit unit = new RoomUnit();
        WiredActionMemory.record(unit, WiredUserActionType.WAVE, -1, 1_000L);
        WiredActionMemory.record(unit, WiredUserActionType.SIT, -1, 1_200L);

        assertTrue(WiredActionMemory.recentExpression(unit, WiredUserActionType.WAVE, 1_450L));
        assertFalse(WiredActionMemory.recentExpression(unit, WiredUserActionType.WAVE, 3_100L));
        assertFalse(WiredActionMemory.recentExpression(unit, WiredUserActionType.RESPECT, 1_450L));
    }
}
