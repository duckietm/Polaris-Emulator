package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredTimerInputGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredTriggerPayloadGuardTest {
    @Test
    void repeaterPayloadsFallBackOnInvalidDataAndClampUpperBound() {
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY,
            WiredTimerInputGuard.normalizeStoredMillis(null, 500, WiredTriggerRepeater.DEFAULT_DELAY));
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY,
            WiredTimerInputGuard.normalizeStoredMillis(0, 500, WiredTriggerRepeater.DEFAULT_DELAY));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
            WiredTimerInputGuard.normalizeStoredMillis(Integer.MAX_VALUE, 500, WiredTriggerRepeater.DEFAULT_DELAY));

        assertEquals(WiredTriggerRepeaterLong.DEFAULT_DELAY,
            WiredTimerInputGuard.normalizeStoredMillis(null, 5000, WiredTriggerRepeaterLong.DEFAULT_DELAY));
        assertEquals(WiredTriggerRepeaterLong.DEFAULT_DELAY,
            WiredTimerInputGuard.normalizeStoredMillis(1, 5000, WiredTriggerRepeaterLong.DEFAULT_DELAY));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
            WiredTimerInputGuard.normalizeStoredMillis(Integer.MAX_VALUE, 5000, WiredTriggerRepeaterLong.DEFAULT_DELAY));
    }

    @Test
    void atTimePayloadsFallBackOnInvalidDataAndClampUpperBound() {
        assertEquals(20 * 500, WiredTimerInputGuard.normalizeStoredMillis(null, 500, 20 * 500));
        assertEquals(20 * 500, WiredTimerInputGuard.normalizeStoredMillis(0, 500, 20 * 500));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
            WiredTimerInputGuard.normalizeStoredMillis(Integer.MAX_VALUE, 500, 20 * 500));

        assertEquals(20 * 5000, WiredTimerInputGuard.normalizeStoredMillis(null, 5000, 20 * 5000));
        assertEquals(20 * 5000, WiredTimerInputGuard.normalizeStoredMillis(1, 5000, 20 * 5000));
        assertEquals(WiredTimerInputGuard.MAX_TIMER_MS,
            WiredTimerInputGuard.normalizeStoredMillis(Integer.MAX_VALUE, 5000, 20 * 5000));
    }

    @Test
    void scorePayloadsNormalizeScoreAndTeam() {
        WiredTriggerScoreAchieved.JsonData invalid = WiredTriggerScoreAchieved.parseData("{broken");
        assertEquals(0, invalid.score);
        assertEquals(GameTeamColors.NONE.type, invalid.teamType);

        WiredTriggerScoreAchieved.JsonData legacy = WiredTriggerScoreAchieved.parseData("-10");
        assertEquals(0, legacy.score);
        assertEquals(GameTeamColors.NONE.type, legacy.teamType);

        WiredTriggerScoreAchieved.JsonData capped = WiredTriggerScoreAchieved.parseData("{\"score\":2147483647,\"teamType\":999}");
        assertEquals(WiredTriggerScoreAchieved.MAX_SCORE, capped.score);
        assertEquals(GameTeamColors.NONE.type, capped.teamType);

        WiredTriggerScoreAchieved.JsonData validTeam = WiredTriggerScoreAchieved.parseData("{\"score\":50,\"teamType\":" + GameTeamColors.RED.type + "}");
        assertEquals(50, validTeam.score);
        assertEquals(GameTeamColors.RED.type, validTeam.teamType);
    }
}
