package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import org.junit.jupiter.api.Test;

/** Habbo's results for furni conditions whose picked selection is empty. */
class WiredConditionEmptySelectionTest {

    private static WiredContext context() {
        WiredContext context = mock(WiredContext.class);
        when(context.room()).thenReturn(mock(Room.class));
        return context;
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
}
