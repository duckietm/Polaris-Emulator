package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.quantified;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.settings;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.user;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredComparison;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

/** "User has rank": the triggering user's account rank compared to a number the builder types. */
class WiredConditionUserHasRankTest {

    @Test
    void passesWhenTheRankComparesAsAsked() {
        Room room = room(1);
        RoomUnit actor = user(room, 10, 5);
        WiredConditionUserHasRank box = new WiredConditionUserHasRank(1, 1, boxBase(), "", 0, 0);

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
        WiredConditionNotUserHasRank box = new WiredConditionNotUserHasRank(1, 1, boxBase(), "", 0, 0);

        box.saveData(settings(new int[] {3, WiredComparison.GREATER_EQUAL, 0}));
        assertTrue(box.evaluate(context(room, actor)));

        box.saveData(settings(new int[] {2, WiredComparison.GREATER_EQUAL, 0}));
        assertFalse(box.evaluate(context(room, actor)));
    }

    @Test
    void withoutAUserAllHoldsAndAnyDoesNot() {
        Room room = room(1);
        WiredConditionUserHasRank box = new WiredConditionUserHasRank(1, 1, boxBase(), "", 0, 0);
        box.saveData(quantified(new int[] {1, WiredComparison.GREATER_EQUAL}, 0));
        assertTrue(box.evaluate(context(room, null)));

        box.saveData(quantified(new int[] {1, WiredComparison.GREATER_EQUAL}, 1));
        assertFalse(box.evaluate(context(room, null)));

        // "All lack the rank" over nobody holds as well.
        assertTrue(new WiredConditionNotUserHasRank(2, 1, boxBase(), "", 0, 0).evaluate(context(room, null)));
    }

    @Test
    void aRankOutsideTheRangeIsClampedAndAnUnknownOperatorMeansAtLeast() {
        WiredConditionUserHasRank box = new WiredConditionUserHasRank(1, 1, boxBase(), "", 0, 0);
        box.saveData(settings(new int[] {-4, 99}));

        Room room = room(1);
        assertTrue(box.evaluate(context(room, user(room, 10, 1))));
    }
}
