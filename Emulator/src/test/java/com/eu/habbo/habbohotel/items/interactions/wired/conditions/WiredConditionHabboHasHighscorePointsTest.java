package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.settings;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredComparison;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.highscores.WiredHighscoreDataEntry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** "User has X points on the scoreboard": the user's best score on the selected boards, compared. */
class WiredConditionHabboHasHighscorePointsTest {

    @Test
    void readsTheUsersBestScoreOnTheSelectedBoards() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 1);
        WiredConditionHabboHasHighscorePoints box =
                new WiredConditionHabboHasHighscorePoints(1, 1, boxBase(), "", 0, 0);
        box.scores(Map.of(
                700, List.of(entry(700, List.of(10), 15), entry(700, List.of(10, 11), 40), entry(700, List.of(11), 99)),
                701, List.of(entry(701, List.of(10), 25)))::get);

        box.saveData(settings(new int[] {40, WiredComparison.GREATER_EQUAL, 0}, 700));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {41, WiredComparison.GREATER_EQUAL, 0}, 700));
        assertFalse(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {25, WiredComparison.EQUAL, 0}, 701));
        assertTrue(box.evaluate(context(room, actor)));
    }

    @Test
    void aUserWithNoEntryHasZeroPoints() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 1);
        WiredConditionHabboHasHighscorePoints box =
                new WiredConditionHabboHasHighscorePoints(1, 1, boxBase(), "", 0, 0);
        box.scores(Map.of(700, List.of(entry(700, List.of(11), 99)))::get);

        box.saveData(settings(new int[] {0, WiredComparison.EQUAL, 0}, 700));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {1, WiredComparison.GREATER_EQUAL, 0}, 700));
        assertFalse(box.evaluate(context(room, actor)));
    }

    @Test
    void theNegativeBoxAnswersTheOppositeAndNoBoardMeansNoPass() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 1);
        WiredConditionNotHabboHasHighscorePoints box =
                new WiredConditionNotHabboHasHighscorePoints(1, 1, boxBase(), "", 0, 0);
        box.scores(Map.of(700, List.of(entry(700, List.of(10), 50)))::get);

        box.saveData(settings(new int[] {50, WiredComparison.GREATER_EQUAL, 0}, 700));
        assertFalse(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {51, WiredComparison.GREATER_EQUAL, 0}, 700));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {51, WiredComparison.GREATER_EQUAL, 0}));
        assertFalse(box.evaluate(context(room, actor)));
    }

    private static WiredHighscoreDataEntry entry(int boardId, List<Integer> userIds, int score) {
        return new WiredHighscoreDataEntry(boardId, userIds, score, true, 0);
    }
}
