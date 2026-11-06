package polaris.hotel.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.items.interactions.wired.triggers.WiredTriggerUserPerformsAction;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.migrate.WiredEvents;

/** The official user action ids (WiredUserAction) and the sign / dance extra in the string param. */
class WiredUserActionOfficialTest {

    @Test
    void theIdsAreTheOfficialOnes() {
        assertEquals(0, WiredUserActionType.WAVE);
        assertEquals(3, WiredUserActionType.RESPECT);
        assertEquals(5, WiredUserActionType.RELAX);
        assertEquals(10, WiredUserActionType.SIGN);
        assertEquals(11, WiredUserActionType.DANCE);
    }

    @Test
    void oldSavedIdsConvert() {
        assertEquals(WiredUserActionType.WAVE, WiredUserActionType.fromLegacy(1));
        assertEquals(WiredUserActionType.LAUGH, WiredUserActionType.fromLegacy(3));
        assertEquals(WiredUserActionType.SIT, WiredUserActionType.fromLegacy(6));
        assertEquals(WiredUserActionType.SIGN, WiredUserActionType.fromLegacy(9));
        assertEquals(WiredUserActionType.DANCE, WiredUserActionType.fromLegacy(10));
        assertEquals(WiredUserActionType.RESPECT, WiredUserActionType.fromLegacy(11));
    }

    @Test
    void theExtraIsTheOfficialString() {
        assertEquals("4", WiredUserActionType.extraString(WiredUserActionType.SIGN, true, 4, false, 1));
        assertEquals("dance 3", WiredUserActionType.extraString(WiredUserActionType.DANCE, false, 0, true, 3));
        assertEquals("", WiredUserActionType.extraString(WiredUserActionType.DANCE, false, 0, false, 3));
        assertEquals(3, WiredUserActionType.extraCode(WiredUserActionType.DANCE, "dance 3"));
        assertEquals(17, WiredUserActionType.extraCode(WiredUserActionType.SIGN, "17"));
        assertEquals(-1, WiredUserActionType.extraCode(WiredUserActionType.SIGN, "18"));
        assertEquals(-1, WiredUserActionType.extraCode(WiredUserActionType.DANCE, ""));
    }

    @Test
    void aTriggerSavesTheDanceFilterAndMatchesOnlyThatDance() {
        WiredTriggerUserPerformsAction trigger = new WiredTriggerUserPerformsAction(1, 1, mock(Item.class), "", 0, 0);
        assertTrue(
                trigger.saveData(new WiredSettings(new int[] {WiredUserActionType.DANCE}, "dance 2", new int[0], -1)));

        Room room = mock(Room.class);
        RoomUnit dancer = mock(RoomUnit.class);
        assertTrue(
                trigger.matches(trigger, WiredEvents.userPerformsAction(room, dancer, WiredUserActionType.DANCE, 2)));
        assertFalse(
                trigger.matches(trigger, WiredEvents.userPerformsAction(room, dancer, WiredUserActionType.DANCE, 3)));
        assertTrue(trigger.getWiredData().contains("\"actionIds\":2"), trigger.getWiredData());
    }

    @Test
    void waveStillTriggersNowThatItIsZero() {
        WiredTriggerUserPerformsAction trigger = new WiredTriggerUserPerformsAction(1, 1, mock(Item.class), "", 0, 0);
        trigger.saveData(new WiredSettings(new int[] {WiredUserActionType.WAVE}, "", new int[0], -1));

        RoomUnit waver = mock(RoomUnit.class);
        assertTrue(trigger.matches(
                trigger, WiredEvents.userPerformsAction(mock(Room.class), waver, WiredUserActionType.WAVE, -1)));
    }

    @Test
    void aBoxSavedWithTheOldIdsKeepsItsAction() throws Exception {
        WiredTriggerUserPerformsAction trigger = new WiredTriggerUserPerformsAction(1, 1, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        // Old id 10 was dance; without "actionIds" the row predates the official ids.
        when(row.getString("wired_data"))
                .thenReturn("{\"selectedAction\":10,\"signFilterEnabled\":false,\"signId\":0,"
                        + "\"danceFilterEnabled\":true,\"danceId\":4}");
        trigger.loadWiredData(row, null);

        assertTrue(trigger.getWiredData().contains("\"selectedAction\":11"), trigger.getWiredData());
        assertTrue(trigger.matches(
                trigger,
                WiredEvents.userPerformsAction(mock(Room.class), mock(RoomUnit.class), WiredUserActionType.DANCE, 4)));
    }
}
