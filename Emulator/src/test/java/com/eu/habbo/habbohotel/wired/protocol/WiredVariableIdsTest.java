package com.eu.habbo.habbohotel.wired.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import org.junit.jupiter.api.Test;

/** Variable ids as the variable list names them, against the tokens our boxes store. */
class WiredVariableIdsTest {

    @Test
    void aBoxVariableGoesOutUnderItsVariableListId() {
        assertEquals("user:12", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_USER, "custom:12"));
        assertEquals("furni:12", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_FURNI, "custom:12"));
        assertEquals("room:12", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_GLOBAL, "custom:12"));
        assertEquals("ctx:12", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_CONTEXT, "custom:12"));
        assertEquals("user:12", WiredVariableIds.toWireForHolder(0, "12"));
        assertEquals("", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_USER, ""));
        assertEquals("room:5", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_GLOBAL, 5));
        assertEquals("", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_GLOBAL, 0));
    }

    @Test
    void aBuiltInGoesOutUnderHabbosIdOrItsKey() {
        assertEquals("-125", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_FURNI, "internal:@position"));
        assertEquals("-120", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_FURNI, "internal:@position.x"));
        assertEquals("-230", WiredVariableIds.toWireForHolder(0, "internal:@position_x"));
        assertEquals(
                "~133", WiredVariableIds.toWire(RoomWiredVariableCatalog.TARGET_FURNI, "internal:~chest.capacity"));
        assertEquals("-420", WiredVariableIds.toWireForHolder(2, "internal:@antenna_id"));
        assertEquals("-320", WiredVariableIds.toWireForHolder(3, "internal:@team_red_score"));
        // Not one of Habbo's: our key under the target's prefix.
        assertEquals("furni:@opacity", WiredVariableIds.toWireForHolder(1, "internal:@opacity"));
    }

    @Test
    void everyFormReadsBackIntoTheToken() {
        assertEquals("custom:12", WiredVariableIds.toToken("user:12"));
        assertEquals("custom:12", WiredVariableIds.toToken("ctx:12"));
        assertEquals("custom:12", WiredVariableIds.toToken("12"));
        assertEquals("custom:12", WiredVariableIds.toToken("custom:12"));
        assertEquals("internal:@position", WiredVariableIds.toToken("-125"));
        assertEquals("internal:@position_x", WiredVariableIds.toToken("-230"));
        assertEquals("internal:~chest.capacity", WiredVariableIds.toToken("~133"));
        assertEquals("internal:@opacity", WiredVariableIds.toToken("furni:@opacity"));
        assertEquals("internal:@position_x", WiredVariableIds.toToken("internal:@position.x"));
        assertEquals("", WiredVariableIds.toToken(WiredVariableIds.NONE));
        assertEquals("", WiredVariableIds.toToken("-99999"));
        assertEquals("", WiredVariableIds.toToken("custom:0"));
        assertEquals("", WiredVariableIds.toToken(null));
        assertEquals(12, WiredVariableIds.boxIdOf("furni:12"));
        assertEquals(0, WiredVariableIds.boxIdOf("-125"));
    }

    @Test
    void anIdNamesItsTarget() {
        assertEquals(RoomWiredVariableCatalog.TARGET_FURNI, WiredVariableIds.officialTargetOf("furni:3"));
        assertEquals(RoomWiredVariableCatalog.TARGET_GLOBAL, WiredVariableIds.officialTargetOf("room:3"));
        assertEquals(RoomWiredVariableCatalog.TARGET_USER, WiredVariableIds.officialTargetOf("-230"));
        assertEquals(RoomWiredVariableCatalog.TARGET_CONTEXT, WiredVariableIds.officialTargetOf("-400"));
        assertEquals(Integer.MIN_VALUE, WiredVariableIds.officialTargetOf("3"));
    }

    @Test
    void slotsKeepTheirPlace() {
        assertArrayEquals(new String[] {"user:1"}, WiredVariableIds.list("user:1", ""));
        assertArrayEquals(new String[] {"n", "room:2"}, WiredVariableIds.list("", "room:2"));
        assertArrayEquals(new String[0], WiredVariableIds.list("", null));
        assertEquals("custom:2", WiredVariableIds.tokenAt(new String[] {"n", "room:2"}, 1));
        assertEquals("", WiredVariableIds.tokenAt(new String[] {"n", "room:2"}, 0));
        assertEquals("", WiredVariableIds.tokenAt(new String[] {"user:1"}, 3));
        assertTrue(WiredVariableIds.present(new String[] {"n"}));
        assertFalse(WiredVariableIds.present(new String[] {""}));
        assertFalse(WiredVariableIds.present(null));
    }
}
