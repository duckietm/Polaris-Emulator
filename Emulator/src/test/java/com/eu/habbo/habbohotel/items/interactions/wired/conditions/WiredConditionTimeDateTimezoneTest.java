package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

/** Time / Date Matches (28 / 29): AIR's timezone dropdown is the string param; empty follows the room. */
class WiredConditionTimeDateTimezoneTest {
    private static final String EAST = "Pacific/Kiritimati"; // UTC+14
    private static final String WEST = "Etc/GMT+12"; // UTC-12

    private static WiredSettings settings(int[] ints, String timezone) {
        return new WiredSettings(ints, timezone, new int[0], 0);
    }

    /** Hours "exactly h" only. */
    private static int[] exactHour(int hour) {
        return new int[] {0, 0, 1, 0, 59, 0, 59, hour, hour};
    }

    @Test
    void theTimezoneIsSavedSentAndUsed() {
        Room room = WiredConditionTestSupport.room(1);
        int eastHour = ZonedDateTime.now(ZoneId.of(EAST)).getHour();

        WiredConditionMatchTime east = new WiredConditionMatchTime(1, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        east.saveData(settings(exactHour(eastHour), " " + EAST + " "));
        assertEquals(EAST, WiredLegacyWireReader.read(east, room).stringParam);
        assertTrue(east.getWiredData().contains("\"timezone\":\"" + EAST + "\""));
        assertTrue(east.evaluate(WiredConditionTestSupport.context(room, null)));

        // Twenty-six hours west: another hour of the day.
        WiredConditionMatchTime west = new WiredConditionMatchTime(2, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        west.saveData(settings(exactHour(eastHour), WEST));
        assertFalse(west.evaluate(WiredConditionTestSupport.context(room, null)));
    }

    @Test
    void anUnknownZoneFallsBackToTheRoomsAndOldBoxesFollowTheRoom() throws Exception {
        Room room = WiredConditionTestSupport.room(1);
        when(room.getWiredTimezone()).thenReturn(EAST);
        int eastHour = ZonedDateTime.now(ZoneId.of(EAST)).getHour();

        WiredConditionMatchTime unknown =
                new WiredConditionMatchTime(3, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        unknown.saveData(settings(exactHour(eastHour), "Mars/Olympus"));
        assertEquals("", WiredLegacyWireReader.read(unknown, room).stringParam);
        assertTrue(unknown.evaluate(WiredConditionTestSupport.context(room, null)));

        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn("{\"hourMode\":1,\"hourFrom\":" + eastHour + ",\"hourTo\":" + eastHour
                        + ",\"minuteMode\":0,\"minuteFrom\":0,\"minuteTo\":0,\"secondMode\":0,\"secondFrom\":0,"
                        + "\"secondTo\":0}");
        WiredConditionMatchTime old = new WiredConditionMatchTime(4, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        old.loadWiredData(set, room);
        assertEquals("", WiredLegacyWireReader.read(old, room).stringParam);
        assertTrue(old.evaluate(WiredConditionTestSupport.context(room, null)));
    }

    @Test
    void dateMatchesUsesItsTimezoneToo() throws Exception {
        Room room = WiredConditionTestSupport.room(1);
        ZonedDateTime east = ZonedDateTime.now(ZoneId.of(EAST));
        ZonedDateTime west = ZonedDateTime.now(ZoneId.of(WEST));
        // Kiritimati and GMT-12 are a day (or more) apart: their days of the month differ.
        int day = east.getDayOfMonth();
        int[] exactDay = {1, 0, 127, day, day, 4095, 0, 0};

        WiredConditionMatchDate eastBox =
                new WiredConditionMatchDate(5, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        eastBox.saveData(settings(exactDay, EAST));
        assertEquals(EAST, WiredLegacyWireReader.read(eastBox, room).stringParam);
        assertTrue(eastBox.evaluate(WiredConditionTestSupport.context(room, null)));

        WiredConditionMatchDate westBox =
                new WiredConditionMatchDate(6, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        westBox.saveData(settings(exactDay, WEST));
        assertEquals(west.getDayOfMonth() == day, westBox.evaluate(WiredConditionTestSupport.context(room, null)));

        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(eastBox.getWiredData());
        WiredConditionMatchDate reopened =
                new WiredConditionMatchDate(7, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        reopened.loadWiredData(set, room);
        assertEquals(EAST, WiredLegacyWireReader.read(reopened, room).stringParam);
    }
}
