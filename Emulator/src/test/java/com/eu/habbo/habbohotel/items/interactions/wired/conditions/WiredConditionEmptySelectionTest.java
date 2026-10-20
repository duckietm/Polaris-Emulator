package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.boxBase;
import static com.eu.habbo.habbohotel.items.interactions.wired.conditions.WiredConditionTestSupport.room;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredCondition;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Habbo's results for conditions whose selection is empty: "all match" holds over nothing and
 * "any matches" does not, and a "not" twin flips both (official capture of Has Variable and
 * Variable Value). The furni-on-furni and furni-has-avatars boxes keep their own observed results.
 */
class WiredConditionEmptySelectionTest {

    private static final int ALL = 0;
    private static final int ANY = 1;

    private static WiredContext context() {
        WiredContext context = mock(WiredContext.class);
        when(context.room()).thenReturn(mock(Room.class));
        return context;
    }

    /** No triggering user, so every "triggering user" source resolves to nobody. */
    private static WiredContext nobody() {
        return WiredConditionTestSupport.context(room(1), null);
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    private static <T extends InteractionWiredCondition> T load(T box, String json) throws SQLException {
        box.loadWiredData(row(json), null);
        return box;
    }

    @Test
    void theFoldHoldsAllOverNothingAndAnyNeedsOne() {
        assertTrue(WiredQuantifier.fold(List.of(), true));
        assertFalse(WiredQuantifier.fold(List.of(), false));
        assertTrue(WiredQuantifier.fold(List.of(true, true), true));
        assertFalse(WiredQuantifier.fold(List.of(true, false), true));
        assertTrue(WiredQuantifier.fold(List.of(false, true), false));
        assertTrue(WiredQuantifier.overEmpty(true));
        assertFalse(WiredQuantifier.overEmpty(false));
        assertFalse(WiredQuantifier.overEmptyNegated(true));
        assertTrue(WiredQuantifier.overEmptyNegated(false));
    }

    @Test
    void furniHaveAvatarsPassesAnEmptySelection() {
        assertTrue(new WiredConditionFurniHaveHabbo(1, 1, null, "", 0, 0).evaluate(context()));
    }

    @Test
    void negatedFurniHasFurniFailsAnEmptySelectionLikeItsPositiveForm() {
        assertFalse(new WiredConditionFurniHaveFurni(1, 1, null, "", 0, 0).evaluate(context()));
        assertFalse(new WiredConditionNotFurniHaveFurni(1, 1, null, "", 0, 0).evaluate(context()));
    }

    @Test
    void hasVariableOverNoUsersPassesAllAndFailsAnyAndTheNotTwinFlips() throws Exception {
        String all = "{\"variableToken\":\"internal:@level\",\"quantifier\":" + ALL + "}";
        String any = "{\"variableToken\":\"internal:@level\",\"quantifier\":" + ANY + "}";

        assertTrue(load(new WiredConditionHasVariable(1, 1, boxBase(), "", 0, 0), all)
                .evaluate(nobody()));
        assertFalse(load(new WiredConditionHasVariable(1, 1, boxBase(), "", 0, 0), any)
                .evaluate(nobody()));
        assertFalse(load(new WiredConditionNotHasVariable(1, 1, boxBase(), "", 0, 0), all)
                .evaluate(nobody()));
        assertTrue(load(new WiredConditionNotHasVariable(1, 1, boxBase(), "", 0, 0), any)
                .evaluate(nobody()));
    }

    @Test
    void userConditionsOverNoUsersFollowTheSameRule() throws Exception {
        String all = "{\"quantifier\":" + ALL + "}";
        String any = "{\"quantifier\":" + ANY + "}";

        assertEquals(
                List.of(true, false, false, true),
                List.of(
                        load(new WiredConditionHabboHasEffect(1, 1, boxBase(), "", 0, 0), all)
                                .evaluate(nobody()),
                        load(new WiredConditionHabboHasEffect(1, 1, boxBase(), "", 0, 0), any)
                                .evaluate(nobody()),
                        load(new WiredConditionNotHabboHasEffect(1, 1, boxBase(), "", 0, 0), all)
                                .evaluate(nobody()),
                        load(new WiredConditionNotHabboHasEffect(1, 1, boxBase(), "", 0, 0), any)
                                .evaluate(nobody())));

        assertEquals(
                List.of(true, false, false, true),
                List.of(
                        load(new WiredConditionUserPerformsAction(1, 1, boxBase(), "", 0, 0), all)
                                .evaluate(nobody()),
                        load(new WiredConditionUserPerformsAction(1, 1, boxBase(), "", 0, 0), any)
                                .evaluate(nobody()),
                        load(new WiredConditionNotUserPerformsAction(1, 1, boxBase(), "", 0, 0), all)
                                .evaluate(nobody()),
                        load(new WiredConditionNotUserPerformsAction(1, 1, boxBase(), "", 0, 0), any)
                                .evaluate(nobody())));
    }
}
