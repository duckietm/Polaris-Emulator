package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.context;
import static polaris.hotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * Every negative condition is the positive box with the same quantifier, turned around: "any" is
 * "not any matches" and "all" is "not all match". Two boxes used to read their quantifier the other
 * way round; these tests pin the convention for both, against two targets of which one matches.
 */
class WiredConditionNegativeQuantifierTest {

    private static final int ALL = 0;
    private static final int ANY = 1;

    @Test
    void notFurniTypeMatchTurnsThePositiveAnswerAround() throws Exception {
        WiredContext ctx = context(room(1), null);

        // One of two targets matches: "any" holds, "all" does not.
        OneOfTwoFurniMatches positive = new OneOfTwoFurniMatches();
        NotOneOfTwoFurniMatches negative = new NotOneOfTwoFurniMatches();

        positive.loadWiredData(row("{\"quantifier\":" + ANY + "}"), null);
        negative.loadWiredData(row("{\"quantifier\":" + ANY + "}"), null);
        assertTrue(positive.evaluate(ctx));
        assertFalse(negative.evaluate(ctx), "any matches, so 'not any' fails");

        positive.loadWiredData(row("{\"quantifier\":" + ALL + "}"), null);
        negative.loadWiredData(row("{\"quantifier\":" + ALL + "}"), null);
        assertFalse(positive.evaluate(ctx));
        assertTrue(negative.evaluate(ctx), "not all match, so 'not all' passes");
    }

    @Test
    void notUserPerformsActionTurnsThePositiveAnswerAround() throws Exception {
        Room room = room(1);
        WiredContext ctx = context(room, null);
        RoomUnit waving = mock(RoomUnit.class);
        RoomUnit still = mock(RoomUnit.class);

        OneOfTwoUsersActs positive = new OneOfTwoUsersActs(waving);
        NotOneOfTwoUsersActs negative = new NotOneOfTwoUsersActs(waving);

        try (MockedStatic<WiredSourceUtil> sources = mockStatic(WiredSourceUtil.class)) {
            sources.when(() -> WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER))
                    .thenReturn(List.of(waving, still));

            positive.loadWiredData(row("{\"quantifier\":" + ANY + "}"), null);
            negative.loadWiredData(row("{\"quantifier\":" + ANY + "}"), null);
            assertTrue(positive.evaluate(ctx));
            assertFalse(negative.evaluate(ctx), "any acts, so 'not any' fails");

            positive.loadWiredData(row("{\"quantifier\":" + ALL + "}"), null);
            negative.loadWiredData(row("{\"quantifier\":" + ALL + "}"), null);
            assertFalse(positive.evaluate(ctx));
            assertTrue(negative.evaluate(ctx), "not all act, so 'not all' passes");
            assertEquals(!positive.evaluate(ctx), negative.evaluate(ctx));
        }
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    /** A furni-type box whose two targets are decided here: one matches, one does not. */
    private static final class OneOfTwoFurniMatches extends WiredConditionFurniTypeMatch {
        private OneOfTwoFurniMatches() {
            super(1, 1, boxBase(), "", 0, 0);
        }

        @Override
        protected boolean evaluateAllMatches(WiredContext ctx) {
            return false;
        }

        @Override
        protected boolean evaluateAnyMatches(WiredContext ctx) {
            return true;
        }
    }

    private static final class NotOneOfTwoFurniMatches extends WiredConditionNotFurniTypeMatch {
        private NotOneOfTwoFurniMatches() {
            super(2, 1, boxBase(), "", 0, 0);
        }

        @Override
        protected boolean evaluateAllMatches(WiredContext ctx) {
            return false;
        }

        @Override
        protected boolean evaluateAnyMatches(WiredContext ctx) {
            return true;
        }
    }

    /** A user-action box that sees only one user acting. */
    private static final class OneOfTwoUsersActs extends WiredConditionUserPerformsAction {
        private final RoomUnit acting;

        private OneOfTwoUsersActs(RoomUnit acting) {
            super(3, 1, boxBase(), "", 0, 0);
            this.acting = acting;
        }

        @Override
        protected boolean matchesAction(WiredContext ctx, RoomUnit roomUnit) {
            return roomUnit == this.acting;
        }
    }

    private static final class NotOneOfTwoUsersActs extends WiredConditionNotUserPerformsAction {
        private final RoomUnit acting;

        private NotOneOfTwoUsersActs(RoomUnit acting) {
            super(4, 1, boxBase(), "", 0, 0);
            this.acting = acting;
        }

        @Override
        protected boolean matchesAction(WiredContext ctx, RoomUnit roomUnit) {
            return roomUnit == this.acting;
        }
    }
}
