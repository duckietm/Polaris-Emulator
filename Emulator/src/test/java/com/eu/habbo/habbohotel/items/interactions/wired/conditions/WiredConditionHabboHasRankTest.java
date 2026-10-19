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
import org.junit.jupiter.api.Test;

/** "User has rank": the triggering user's account rank compared to a number the builder types. */
class WiredConditionHabboHasRankTest {

    @Test
    void passesWhenTheRankComparesAsAsked() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 5);
        WiredConditionHabboHasRank box = new WiredConditionHabboHasRank(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {5, WiredComparison.GREATER_EQUAL, 0}));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {6, WiredComparison.GREATER_EQUAL, 0}));
        assertFalse(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {5, WiredComparison.EQUAL, 0}));
        assertTrue(box.evaluate(context(room, actor)));
    }

    @Test
    void theNegativeBoxAnswersTheOpposite() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 2);
        WiredConditionNotHabboHasRank box = new WiredConditionNotHabboHasRank(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {3, WiredComparison.GREATER_EQUAL, 0}));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {2, WiredComparison.GREATER_EQUAL, 0}));
        assertFalse(box.evaluate(context(room, actor)));
    }

    @Test
    void withoutAUserThereIsNothingToCompare() {
        Room room = room(1);
        WiredConditionHabboHasRank box = new WiredConditionHabboHasRank(1, 1, boxBase(), "", 0, 0);
        box.saveData(settings(new int[] {1, WiredComparison.GREATER_EQUAL, 0}));

        assertFalse(box.evaluate(context(room, null)));
        assertFalse(new WiredConditionNotHabboHasRank(2, 1, boxBase(), "", 0, 0).evaluate(context(room, null)));
    }

    @Test
    void aRankOutsideTheRangeIsClampedAndAnUnknownOperatorMeansAtLeast() {
        WiredConditionHabboHasRank box = new WiredConditionHabboHasRank(1, 1, boxBase(), "", 0, 0);
        box.saveData(settings(new int[] {-4, 99}));

        Room room = room(1);
        assertTrue(box.evaluate(context(room, user(room, 10, 1))));
    }
}
