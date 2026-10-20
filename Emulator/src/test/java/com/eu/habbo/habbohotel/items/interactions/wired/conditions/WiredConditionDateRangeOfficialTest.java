package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredLegacyWireReader;
import com.eu.habbo.habbohotel.rooms.Room;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** Date Range (24): AIR sends [], [start] or [start, end], and opens empty fields for what is missing. */
class WiredConditionDateRangeOfficialTest {
    private static final int START = 1_800_000_000;
    private static final int END = START + 86_400;

    private static WiredConditionDateRangeActive box() {
        return new WiredConditionDateRangeActive(1, 1, WiredConditionTestSupport.boxBase(), "", 0, 0);
    }

    private static int[] savedParams(int... params) {
        Room room = WiredConditionTestSupport.room(1);
        WiredConditionDateRangeActive box = box();
        assertTrue(box.saveData(WiredConditionTestSupport.settings(params)));
        return WiredLegacyWireReader.read(box, room).intParams;
    }

    @Test
    void zeroOneOrTwoIntsAreSavedAndSentBackTheSameWay() {
        assertArrayEquals(new int[0], savedParams());
        assertArrayEquals(new int[] {START}, savedParams(START));
        assertArrayEquals(new int[] {START, END}, savedParams(START, END));
        // An end before the start is no range at all.
        assertArrayEquals(new int[0], savedParams(END, START));
    }

    @Test
    void aFreshBoxIsEmpty() {
        assertArrayEquals(new int[0], WiredLegacyWireReader.read(box(), WiredConditionTestSupport.room(1)).intParams);
    }

    @Test
    void boxesSavedWithTwoIntsLoadAsBefore() throws Exception {
        Room room = WiredConditionTestSupport.room(1);
        ResultSet set = mock(ResultSet.class);
        WiredConditionDateRangeActive box = box();

        when(set.getString("wired_data")).thenReturn("{\"startDate\":" + START + ",\"endDate\":" + END + "}");
        box.loadWiredData(set, room);
        assertArrayEquals(new int[] {START, END}, WiredLegacyWireReader.read(box, room).intParams);

        // An old client wrote 0 for a field it could not read.
        when(set.getString("wired_data")).thenReturn("{\"startDate\":" + START + ",\"endDate\":0}");
        box.loadWiredData(set, room);
        assertArrayEquals(new int[] {START}, WiredLegacyWireReader.read(box, room).intParams);

        when(set.getString("wired_data")).thenReturn("{\"startDate\":0,\"endDate\":" + END + "}");
        box.loadWiredData(set, room);
        assertArrayEquals(new int[] {0, END}, WiredLegacyWireReader.read(box, room).intParams);
    }
}
