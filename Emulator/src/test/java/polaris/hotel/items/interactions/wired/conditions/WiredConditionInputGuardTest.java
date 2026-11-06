package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.wired.core.WiredSourceUtil;

class WiredConditionInputGuardTest {

    @Test
    void teamColorsAreResolvedByProtocolTypeWithoutArrayIndexing() {
        assertEquals(GameTeamColors.RED, WiredConditionInputGuard.normalizeTeamColorType(1, GameTeamColors.RED));
        assertEquals(GameTeamColors.TEN, WiredConditionInputGuard.normalizeTeamColorType(14, GameTeamColors.RED));
        assertEquals(GameTeamColors.RED, WiredConditionInputGuard.normalizeTeamColorType(-1, GameTeamColors.RED));
        assertEquals(
                GameTeamColors.RED,
                WiredConditionInputGuard.normalizeTeamColorType(Integer.MAX_VALUE, GameTeamColors.RED));
    }

    @Test
    void userSourcesFallBackToTriggerWhenUnknown() {
        assertEquals(WiredSourceUtil.SOURCE_TRIGGER, WiredConditionInputGuard.normalizeUserSource(-100));
        assertEquals(
                WiredSourceUtil.SOURCE_SELECTOR,
                WiredConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SELECTOR));
        assertEquals(
                WiredSourceUtil.SOURCE_SIGNAL,
                WiredConditionInputGuard.normalizeUserSource(WiredSourceUtil.SOURCE_SIGNAL));
    }

    @Test
    void userCountRangesArePositiveOrderedAndCapped() {
        assertArrayEquals(new int[] {0, 50}, WiredConditionInputGuard.normalizeUserCountRange(-50, 50));
        assertArrayEquals(new int[] {10, 20}, WiredConditionInputGuard.normalizeUserCountRange(20, 10));
        assertArrayEquals(
                new int[] {1000, 1000},
                WiredConditionInputGuard.normalizeUserCountRange(Integer.MAX_VALUE, Integer.MAX_VALUE));
    }

    @Test
    void elapsedTimerCyclesArePositiveAndBounded() {
        assertEquals(0, WiredConditionInputGuard.normalizeTimerCycles(-1));
        assertEquals(42, WiredConditionInputGuard.normalizeTimerCycles(42));
        assertEquals(
                WiredConditionInputGuard.MAX_TIMER_CYCLES,
                WiredConditionInputGuard.normalizeTimerCycles(Integer.MAX_VALUE));
    }
}
