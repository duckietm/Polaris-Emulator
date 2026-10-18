package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Inside a Give Variable every write counts as created; a give that kept the value fires nothing. */
class WiredVariableGiveScopeTest {

    @Test
    void outsideAGiveOnlyANewVariableIsCreated() {
        assertTrue(WiredVariableGiveScope.created(false, true));
        assertFalse(WiredVariableGiveScope.created(true, true));
        assertFalse(WiredVariableGiveScope.suppresses(true, 5, true, 5));
    }

    @Test
    void insideAGiveAnOverrideIsCreatedAndAnUnchangedValueIsSilent() {
        assertTrue(WiredVariableGiveScope.give(() -> {
            assertTrue(WiredVariableGiveScope.created(true, true));
            assertTrue(WiredVariableGiveScope.suppresses(true, 5, true, 5));
            assertFalse(WiredVariableGiveScope.suppresses(true, 5, true, 6));
            assertFalse(WiredVariableGiveScope.created(true, false));
            return true;
        }));
        assertFalse(WiredVariableGiveScope.active());
    }
}
