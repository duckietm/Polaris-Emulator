package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WiredTriggerPayloadGuardTest {
    @Test
    void repeaterPayloadsFallBackOnInvalidDataAndClampUpperBound() {
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY, WiredTriggerRepeater.parseRepeatTime(null));
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY, WiredTriggerRepeater.parseRepeatTime("not-a-number"));
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY, WiredTriggerRepeater.parseRepeatTime("{broken"));
        assertEquals(WiredTriggerRepeater.DEFAULT_DELAY, WiredTriggerRepeater.parseRepeatTime("{\"repeatTime\":0}"));
        assertEquals(WiredTriggerRepeater.MAX_DELAY, WiredTriggerRepeater.parseRepeatTime("{\"repeatTime\":2147483647}"));

        assertEquals(WiredTriggerRepeaterLong.DEFAULT_DELAY, WiredTriggerRepeaterLong.parseRepeatTime(null));
        assertEquals(WiredTriggerRepeaterLong.DEFAULT_DELAY, WiredTriggerRepeaterLong.parseRepeatTime("1"));
        assertEquals(WiredTriggerRepeaterLong.MAX_DELAY, WiredTriggerRepeaterLong.parseRepeatTime("2147483647"));
    }

    @Test
    void atTimePayloadsFallBackOnInvalidDataAndClampUpperBound() {
        assertEquals(WiredTriggerAtSetTime.DEFAULT_EXECUTE_TIME, WiredTriggerAtSetTime.parseExecuteTime(null));
        assertEquals(WiredTriggerAtSetTime.DEFAULT_EXECUTE_TIME, WiredTriggerAtSetTime.parseExecuteTime("bad"));
        assertEquals(WiredTriggerAtSetTime.DEFAULT_EXECUTE_TIME, WiredTriggerAtSetTime.parseExecuteTime("{\"executeTime\":0}"));
        assertEquals(WiredTriggerAtSetTime.MAX_EXECUTE_TIME, WiredTriggerAtSetTime.parseExecuteTime("{\"executeTime\":2147483647}"));

        assertEquals(WiredTriggerAtTimeLong.DEFAULT_EXECUTE_TIME, WiredTriggerAtTimeLong.parseExecuteTime("{broken"));
        assertEquals(WiredTriggerAtTimeLong.DEFAULT_EXECUTE_TIME, WiredTriggerAtTimeLong.parseExecuteTime("1"));
        assertEquals(WiredTriggerAtTimeLong.MAX_EXECUTE_TIME, WiredTriggerAtTimeLong.parseExecuteTime("2147483647"));
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
