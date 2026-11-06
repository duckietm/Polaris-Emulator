package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.boxBase;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.room;
import static polaris.hotel.items.interactions.wired.triggers.WiredTriggerTestSupport.row;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.core.WiredEvent;

/**
 * Loading a "user says" row must never leave the trigger with a null keyword or crash on a row it
 * cannot read: matches() takes the keyword's length on every chat line in the room.
 */
class WiredTriggerUserSaysKeywordLoadTest {

    @Test
    void aMissingRowLeavesTheDefaults() throws Exception {
        WiredTriggerUserSaysKeyword trigger = configured();

        trigger.loadWiredData(row(null), null);

        assertDefaults(trigger);
    }

    @Test
    void anUnreadableRowLeavesTheDefaults() throws Exception {
        WiredTriggerUserSaysKeyword trigger = configured();

        trigger.loadWiredData(row("{broken"), null);

        assertDefaults(trigger);
    }

    @Test
    void aRowWithoutAKeywordMatchesNothingInsteadOfCrashing() throws Exception {
        WiredTriggerUserSaysKeyword trigger = configured();

        trigger.loadWiredData(row("{\"matchMode\":1,\"ownerOnly\":true}"), null);

        assertEquals("", trigger.getKey());
        assertTrue(trigger.isOwnerOnly());
        assertEquals(1, trigger.getMatchMode());

        Room room = room(1);
        WiredEvent said = WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                .actor(mock(RoomUnit.class))
                .text("hello")
                .build();
        assertFalse(trigger.matches(null, said));
    }

    /** A trigger that already holds a keyword, so a reset is visible. */
    private static WiredTriggerUserSaysKeyword configured() {
        WiredTriggerUserSaysKeyword trigger = new WiredTriggerUserSaysKeyword(1, 1, boxBase(), "", 0, 0);
        trigger.saveData(new WiredSettings(new int[] {1, 1, 1}, "old", new int[0], 0));
        assertEquals("old", trigger.getKey());
        return trigger;
    }

    private static void assertDefaults(WiredTriggerUserSaysKeyword trigger) {
        assertEquals("", trigger.getKey());
        assertFalse(trigger.isOwnerOnly());
        assertFalse(trigger.isHideMessage());
        assertEquals(0, trigger.getMatchMode());
    }
}
