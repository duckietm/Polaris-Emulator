package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredRoomTime;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** Time Elapsed More / Less (3 / 4): AIR's param is the slider's half-seconds plus one. */
class WiredConditionTimeElapsedOfficialTest {

    private static ResultSet stored(String json) throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(json);
        return set;
    }

    @Test
    void theParamIsTheSliderPlusOneBothWays() {
        Room room = WiredConditionTestSupport.room(11);
        WiredConditionMoreTimeElapsed more =
                new WiredConditionMoreTimeElapsed(1, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        WiredConditionLessTimeElapsed less =
                new WiredConditionLessTimeElapsed(2, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);

        assertTrue(more.saveData(WiredConditionTestSupport.settings(new int[] {21})));
        assertTrue(less.saveData(WiredConditionTestSupport.settings(new int[] {2})));
        assertArrayEquals(new int[] {21}, WiredLegacyWireReader.read(more, room).intParams);
        assertArrayEquals(new int[] {2}, WiredLegacyWireReader.read(less, room).intParams);
        // Stored: the half-seconds the slider shows.
        assertTrue(more.getWiredData().contains("\"cycles\":20"));
        assertTrue(less.getWiredData().contains("\"cycles\":1"));
    }

    @Test
    void aBoxSavedBeforeTheOffsetKeepsItsTimeAndOpensOnIt() throws Exception {
        Room room = WiredConditionTestSupport.room(12);
        WiredConditionMoreTimeElapsed more =
                new WiredConditionMoreTimeElapsed(3, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        // Old clients sent the slider value itself: 20 half-seconds.
        more.loadWiredData(stored("{\"cycles\":20}"), room);
        assertArrayEquals(new int[] {21}, WiredLegacyWireReader.read(more, room).intParams);

        long now = System.currentTimeMillis();
        WiredRoomTime.markTimersReset(room, now - 11_000);
        try {
            assertTrue(more.evaluate(WiredConditionTestSupport.context(room, null)));
            WiredRoomTime.markTimersReset(room, now - 4_000);
            assertFalse(more.evaluate(WiredConditionTestSupport.context(room, null)));
        } finally {
            WiredRoomTime.forgetRoom(room.getId());
        }
    }

    @Test
    void lessComparesAgainstTheShownTime() throws Exception {
        Room room = WiredConditionTestSupport.room(13);
        WiredConditionLessTimeElapsed less =
                new WiredConditionLessTimeElapsed(4, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
        // Slider 20 half-seconds (10 s), param 21.
        assertTrue(less.saveData(WiredConditionTestSupport.settings(new int[] {21})));

        long now = System.currentTimeMillis();
        WiredRoomTime.markTimersReset(room, now - 5_000);
        try {
            assertTrue(less.evaluate(WiredConditionTestSupport.context(room, null)));
            WiredRoomTime.markTimersReset(room, now - 15_000);
            assertFalse(less.evaluate(WiredConditionTestSupport.context(room, null)));
        } finally {
            WiredRoomTime.forgetRoom(room.getId());
        }
    }
}
