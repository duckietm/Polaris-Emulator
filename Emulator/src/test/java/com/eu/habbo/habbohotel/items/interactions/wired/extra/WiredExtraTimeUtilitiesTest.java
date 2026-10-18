package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredExtraTimeUtilitiesTest {
    private static final ZoneId AMSTERDAM = ZoneId.of("Europe/Amsterdam");
    // 2024-03-10 23:30:15 UTC, a Sunday; 00:30:15 on Monday 11 March in Amsterdam.
    private static final int INSTANT =
            (int) ZonedDateTime.of(2024, 3, 10, 23, 30, 15, 0, ZoneOffset.UTC).toEpochSecond();

    private static WiredExtraTimeUtilities box() {
        Item base = mock(Item.class);
        when(base.getSpriteId()).thenReturn(55);
        return new WiredExtraTimeUtilities(9, 1, base, "", 0, 0);
    }

    private static WiredExtraTimeUtilities box(int mask, int mode) {
        WiredExtraTimeUtilities box = box();
        box.saveData(new WiredSettings(new int[] {mask, mode}, "", new int[0], 0), null);
        return box;
    }

    private static int bits(int... ids) {
        int mask = 0;
        for (int id : ids) mask |= 1 << id;
        return mask;
    }

    private static ResultSet row(String wiredData) throws SQLException {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        return set;
    }

    @Test
    void saveKeepsOnlyKnownSubVariablesAndModes() {
        WiredExtraTimeUtilities box = box(-1, 7);

        assertEquals(0x7FE | 0x7F00000, box.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.MODE_VALUE, box.getMode());
        assertFalse(box.hasSubvariable(0));
        assertFalse(box.hasSubvariable(11));
        assertFalse(box.hasSubvariable(19));
        assertTrue(box.hasSubvariable(WiredExtraTimeUtilities.SUB_YEAR));
        assertTrue(box.hasSubvariable(WiredExtraTimeUtilities.SUB_MONTHS));
        assertEquals(17, box.getSelectedSubvariables().size());

        box.saveData(new WiredSettings(new int[] {bits(4)}, "", new int[0], 0), null);
        assertEquals(List.of(4), box.getSelectedSubvariables());
        assertEquals(WiredExtraTimeUtilities.MODE_VALUE, box.getMode());

        box.saveData(new WiredSettings(new int[0], "3", new int[0], 0), null);
        assertEquals(0, box.getSubvariableMask());
    }

    @Test
    void editorGetsMaskAndModeBack() {
        WiredExtraTimeUtilities box = box(bits(4, 10, 24), WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME);
        ServerMessage message = new ServerMessage(1);
        box.serializeWiredData(message, null);
        ByteBuf buffer = message.get();
        try {
            buffer.readInt();
            buffer.readShort();
            buffer.readByte();
            buffer.readInt();
            buffer.readInt();
            assertEquals(55, buffer.readInt());
            assertEquals(9, buffer.readInt());
            assertEquals(0, buffer.readShort());
            int[] params = new int[buffer.readInt()];
            for (int i = 0; i < params.length; i++) params[i] = buffer.readInt();
            assertArrayEquals(new int[] {bits(4, 10, 24), WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME}, params);
            buffer.readInt();
            assertEquals(WiredExtraTimeUtilities.CODE, buffer.readInt());
        } finally {
            buffer.release();
        }
    }

    @Test
    void savedDataRoundTripsAndOldSavesStillLoad() throws Exception {
        WiredExtraTimeUtilities saved = box(bits(1, 26), WiredExtraTimeUtilities.MODE_CREATION_TIME);
        WiredExtraTimeUtilities loaded = box();
        loaded.loadWiredData(row(saved.getWiredData()), null);
        assertEquals(bits(1, 26), loaded.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.MODE_CREATION_TIME, loaded.getMode());

        loaded.loadWiredData(row("{\"timeUnit\":3}"), null);
        assertEquals(WiredExtraTimeUtilities.UNIT_HOURS, loaded.getTimeUnit());
        assertEquals(0, loaded.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.MODE_VALUE, loaded.getMode());

        loaded.loadWiredData(row("0"), null);
        assertEquals(WiredExtraTimeUtilities.UNIT_MILLISECONDS, loaded.getTimeUnit());
        assertEquals(0, loaded.getSubvariableMask());

        loaded.loadWiredData(row("{\"subvariableMask\":-1,\"mode\":9}"), null);
        assertEquals(0x7FE | 0x7F00000, loaded.getSubvariableMask());
        assertEquals(WiredExtraTimeUtilities.MODE_VALUE, loaded.getMode());
    }

    @Test
    void calendarPartsUseTheGivenZone() {
        WiredExtraTimeUtilities box = box(-1, WiredExtraTimeUtilities.MODE_VALUE);

        assertEquals(0, box.derive(WiredExtraTimeUtilities.SUB_MILLISECOND_OF_SECOND, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(15, box.derive(WiredExtraTimeUtilities.SUB_SECONDS_OF_MINUTE, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(30, box.derive(WiredExtraTimeUtilities.SUB_MINUTE_OF_HOUR, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(0, box.derive(WiredExtraTimeUtilities.SUB_HOUR_OF_DAY, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(1, box.derive(WiredExtraTimeUtilities.SUB_DAY_OF_WEEK, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(11, box.derive(WiredExtraTimeUtilities.SUB_DAY_OF_MONTH, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(71, box.derive(WiredExtraTimeUtilities.SUB_DAY_OF_YEAR, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(11, box.derive(WiredExtraTimeUtilities.SUB_WEEK_OF_YEAR, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(3, box.derive(WiredExtraTimeUtilities.SUB_MONTH_OF_YEAR, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(2024, box.derive(WiredExtraTimeUtilities.SUB_YEAR, INSTANT, 0, 0, AMSTERDAM));

        assertEquals(23, box.derive(WiredExtraTimeUtilities.SUB_HOUR_OF_DAY, INSTANT, 0, 0, ZoneOffset.UTC));
        assertEquals(7, box.derive(WiredExtraTimeUtilities.SUB_DAY_OF_WEEK, INSTANT, 0, 0, ZoneOffset.UTC));
        assertEquals(10, box.derive(WiredExtraTimeUtilities.SUB_WEEK_OF_YEAR, INSTANT, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void unitsCountFromNineteenSeventy() {
        WiredExtraTimeUtilities box = box(-1, WiredExtraTimeUtilities.MODE_VALUE);

        assertEquals(Integer.MAX_VALUE, box.derive(WiredExtraTimeUtilities.SUB_MILLISECONDS, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(90_000, box.derive(WiredExtraTimeUtilities.SUB_MILLISECONDS, 90, 0, 0, AMSTERDAM));
        assertEquals(INSTANT, box.derive(WiredExtraTimeUtilities.SUB_SECONDS, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(INSTANT / 60, box.derive(WiredExtraTimeUtilities.SUB_MINUTES, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(INSTANT / 3600, box.derive(WiredExtraTimeUtilities.SUB_HOURS, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(19_792, box.derive(WiredExtraTimeUtilities.SUB_DAYS, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(2_827, box.derive(WiredExtraTimeUtilities.SUB_WEEKS, INSTANT, 0, 0, AMSTERDAM));
        assertEquals(650, box.derive(WiredExtraTimeUtilities.SUB_MONTHS, INSTANT, 0, 0, AMSTERDAM));
        // Before 1970 is no longer clamped: five seconds before is in day -1.
        assertEquals(-1, box.derive(WiredExtraTimeUtilities.SUB_DAYS, -5, 0, 0, AMSTERDAM));
    }

    @Test
    void modesPickTheValueOrATimestamp() {
        int created = INSTANT - 86_400 * 3;
        int updated = INSTANT - 60;

        WiredExtraTimeUtilities byValue = box(bits(24), WiredExtraTimeUtilities.MODE_VALUE);
        assertEquals(19_792, byValue.derive(24, INSTANT, created, updated, AMSTERDAM));
        assertNull(byValue.derive(24, null, created, updated, AMSTERDAM));
        assertFalse(byValue.readsTimestamps());

        WiredExtraTimeUtilities byCreation = box(bits(24), WiredExtraTimeUtilities.MODE_CREATION_TIME);
        assertEquals(19_789, byCreation.derive(24, null, created, updated, AMSTERDAM));
        assertNull(byCreation.derive(24, 5, 0, updated, AMSTERDAM));
        assertTrue(byCreation.readsTimestamps());

        WiredExtraTimeUtilities byUpdate = box(bits(22), WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME);
        assertEquals(updated / 60, byUpdate.derive(22, 0, created, updated, AMSTERDAM));
        assertNull(byUpdate.derive(22, 0, created, 0, AMSTERDAM));
    }

    @Test
    void subVariableNamesAreTheOfficialModeAndPartKeys() {
        WiredExtraTimeUtilities box = box();
        assertEquals("value.milliseconds_of_seconds", box.subvariableKey(1));
        assertEquals("value.week_of_year", box.subvariableKey(8));
        assertEquals("value.year", box.subvariableKey(10));
        assertEquals("value.millisecond", box.subvariableKey(20));
        assertEquals("value.month", box.subvariableKey(26));
        assertEquals(
                "creation_time.year",
                box(bits(10), WiredExtraTimeUtilities.MODE_CREATION_TIME).subvariableKey(10));
        assertEquals(
                "last_update_time.hour_of_day",
                box(bits(4), WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME).subvariableKey(4));
    }

    @Test
    void valuesBefore1970ReadLikeAnyOther() {
        WiredExtraTimeUtilities box = box(bits(10) | bits(21) | bits(26), WiredExtraTimeUtilities.MODE_VALUE);
        assertEquals(1969, box.derive(10, -1, 0, 0, java.time.ZoneOffset.UTC));
        assertEquals(-1, box.derive(21, -1, 0, 0, java.time.ZoneOffset.UTC));
        // Whole months count towards zero: one second before 1970 is not yet a month back.
        assertEquals(0, box.derive(26, -1, 0, 0, java.time.ZoneOffset.UTC));
        assertEquals(-1, box.derive(26, -40 * 86_400, 0, 0, java.time.ZoneOffset.UTC));
    }

    private static int utc(int year, int month, int day, int hour, int minute, int second) {
        return (int) ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC)
                .toEpochSecond();
    }

    private static Integer writeUtc(WiredExtraTimeUtilities box, int part, int value, int partValue) {
        return box.write(part, value, partValue, ZoneOffset.UTC);
    }

    @Test
    void writingAValuePartMovesTheVariableByThatPart() {
        WiredExtraTimeUtilities box = box(-1, WiredExtraTimeUtilities.MODE_VALUE);
        int instant = utc(2024, 3, 10, 23, 30, 15);

        assertEquals(utc(2024, 3, 10, 5, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_HOUR_OF_DAY, instant, 5));
        // Out of range carries over: hour 25 is 1 o'clock the next day.
        assertEquals(utc(2024, 3, 11, 1, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_HOUR_OF_DAY, instant, 25));
        assertEquals(
                utc(2024, 3, 10, 23, 0, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_MINUTE_OF_HOUR, instant, 0));
        assertEquals(
                utc(2024, 3, 10, 23, 30, 59),
                writeUtc(box, WiredExtraTimeUtilities.SUB_SECONDS_OF_MINUTE, instant, 59));
        assertEquals(utc(2024, 3, 1, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_DAY_OF_MONTH, instant, 1));
        // Sunday (7) to Monday (1) of the same ISO week.
        assertEquals(utc(2024, 3, 4, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_DAY_OF_WEEK, instant, 1));
        assertEquals(utc(2024, 1, 1, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_DAY_OF_YEAR, instant, 1));
        assertEquals(
                utc(2024, 3, 17, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_WEEK_OF_YEAR, instant, 11));
        assertEquals(
                utc(2024, 12, 10, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_MONTH_OF_YEAR, instant, 12));
        assertEquals(utc(2030, 3, 10, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_YEAR, instant, 2030));
        assertEquals(utc(1969, 3, 10, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_YEAR, instant, 1969));
    }

    @Test
    void calendarWritesUseTheRoomZone() {
        WiredExtraTimeUtilities box = box(-1, WiredExtraTimeUtilities.MODE_VALUE);
        // 00:30:15 on Monday in Amsterdam; 9 o'clock there is 08:30:15 UTC.
        assertEquals(
                utc(2024, 3, 11, 8, 30, 15), box.write(WiredExtraTimeUtilities.SUB_HOUR_OF_DAY, INSTANT, 9, AMSTERDAM));
    }

    @Test
    void writingAUnitKeepsTheRemainder() {
        WiredExtraTimeUtilities box = box(-1, WiredExtraTimeUtilities.MODE_VALUE);
        int instant = utc(2024, 3, 10, 23, 30, 15);

        assertEquals(1234, writeUtc(box, WiredExtraTimeUtilities.SUB_SECONDS, instant, 1234));
        assertEquals(86_400 + 23 * 3600 + 30 * 60 + 15, writeUtc(box, WiredExtraTimeUtilities.SUB_DAYS, instant, 1));
        assertEquals(30 * 60 + 15, writeUtc(box, WiredExtraTimeUtilities.SUB_HOURS, instant, 0));
        assertEquals(utc(1970, 2, 10, 23, 30, 15), writeUtc(box, WiredExtraTimeUtilities.SUB_MONTHS, instant, 1));
        assertEquals(-2, writeUtc(box, WiredExtraTimeUtilities.SUB_MILLISECONDS, instant, -1500));
        // Our variables hold seconds: a whole second moves, the rest is dropped.
        assertEquals(instant + 1, writeUtc(box, WiredExtraTimeUtilities.SUB_MILLISECOND_OF_SECOND, instant, 1500));
    }

    @Test
    void writesThatDoNotFitOrAreNotValuePartsAreRefused() {
        WiredExtraTimeUtilities value = box(bits(4, 10), WiredExtraTimeUtilities.MODE_VALUE);
        assertTrue(value.isWritable(4));
        assertFalse(value.isWritable(5));
        assertNull(writeUtc(value, 5, INSTANT, 1));
        assertNull(writeUtc(value, 10, INSTANT, 3000));

        WiredExtraTimeUtilities created = box(bits(4), WiredExtraTimeUtilities.MODE_CREATION_TIME);
        assertFalse(created.isWritable(4));
        assertFalse(created.isWritable(WiredExtraTimeUtilities.SUB_TIMESTAMP));
        assertNull(writeUtc(created, 4, INSTANT, 1));
    }

    @Test
    void timestampModesAlsoListTheTimestampItself() {
        WiredExtraTimeUtilities value = box(bits(4), WiredExtraTimeUtilities.MODE_VALUE);
        assertEquals(List.of(4), value.getListedSubvariables());
        assertFalse(value.listsSubvariable(WiredExtraTimeUtilities.SUB_TIMESTAMP));
        assertNull(value.derive(WiredExtraTimeUtilities.SUB_TIMESTAMP, INSTANT, 5, 6, AMSTERDAM));

        WiredExtraTimeUtilities created = box(bits(4), WiredExtraTimeUtilities.MODE_CREATION_TIME);
        assertEquals(List.of(4, WiredExtraTimeUtilities.SUB_TIMESTAMP), created.getListedSubvariables());
        assertEquals("creation_time", created.subvariableKey(WiredExtraTimeUtilities.SUB_TIMESTAMP));
        assertEquals(INSTANT, created.derive(WiredExtraTimeUtilities.SUB_TIMESTAMP, 0, INSTANT, 9, AMSTERDAM));

        WiredExtraTimeUtilities updated = box(0, WiredExtraTimeUtilities.MODE_LAST_UPDATE_TIME);
        assertEquals(List.of(WiredExtraTimeUtilities.SUB_TIMESTAMP), updated.getListedSubvariables());
        assertEquals("last_update_time", updated.subvariableKey(WiredExtraTimeUtilities.SUB_TIMESTAMP));
        assertEquals(INSTANT, updated.derive(WiredExtraTimeUtilities.SUB_TIMESTAMP, 0, 9, INSTANT, AMSTERDAM));
        assertNull(updated.derive(WiredExtraTimeUtilities.SUB_TIMESTAMP, 0, 9, 0, AMSTERDAM));
    }
}
