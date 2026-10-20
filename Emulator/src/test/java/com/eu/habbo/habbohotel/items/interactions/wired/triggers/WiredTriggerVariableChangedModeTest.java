package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredVariableChangeOrigin;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import java.sql.ResultSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Variable Changed's official trigger modes (int 5) and its "another room" origin (bit 1). */
class WiredTriggerVariableChangedModeTest {

    private static WiredEvent changed(Room room, int definitionItemId, int targetType, int origin) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableTargetType(targetType)
                .variableDefinitionItemId(definitionItemId)
                .variableChangeKind(WiredEvent.VariableChangeKind.INCREASED)
                .variableChangeOrigin(origin)
                .build();
    }

    private static Room roomWithItems(int... ids) {
        Room room = mock(Room.class);
        for (int id : ids) {
            when(room.getHabboItem(id)).thenReturn(mock(HabboItem.class));
        }
        return room;
    }

    private static WiredTriggerVariableChanged save(Room room, WiredSettings settings) {
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(70, 1, null, "", 0, 0);
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(trigger.saveData(settings));
        }
        return trigger;
    }

    @Test
    void modeOneWatchesEveryVariableOfThePickedBoxesWithoutAVariablePick() throws Exception {
        Room room = roomWithItems(501, 502);
        // Official layout: created, changed, deleted, every change, every origin, mode 1, user target.
        WiredSettings settings = new WiredSettings(new int[] {1, 1, 1, 7, -1, 1, 1}, "", new int[] {501, 502}, 0);
        WiredTriggerVariableChanged trigger = save(room, settings);

        assertEquals(WiredTriggerVariableChanged.MODE_PICKED_BOXES, trigger.getMode());
        assertEquals(Set.of(501, 502), trigger.getPickedBoxIds());
        // Any holder type: the box names its variable.
        assertTrue(trigger.matches(trigger, changed(room, 501, 1, WiredVariableChangeOrigin.WIRED)));
        assertTrue(trigger.matches(trigger, changed(room, 502, 3, WiredVariableChangeOrigin.WIRED)));
        assertFalse(trigger.matches(trigger, changed(room, 503, 0, WiredVariableChangeOrigin.WIRED)));

        ResultSet stored = mock(ResultSet.class);
        when(stored.getString("wired_data")).thenReturn(trigger.getWiredData());
        WiredTriggerVariableChanged reopened = new WiredTriggerVariableChanged(71, 1, null, "", 0, 0);
        reopened.loadWiredData(stored, room);
        assertEquals(WiredTriggerVariableChanged.MODE_PICKED_BOXES, reopened.getMode());
        assertTrue(reopened.matches(reopened, changed(room, 502, 0, WiredVariableChangeOrigin.WIRED)));
    }

    @Test
    void aBoxSavedBeforeTheModesWatchesItsOneVariable() throws Exception {
        Room room = mock(Room.class);
        ResultSet stored = mock(ResultSet.class);
        when(stored.getString("wired_data"))
                .thenReturn("{\"variableToken\":\"custom:88\",\"variableItemId\":88,\"targetType\":0,"
                        + "\"createdEnabled\":true,\"valueChangedEnabled\":true,\"increasedEnabled\":true,"
                        + "\"decreasedEnabled\":true,\"unchangedEnabled\":true,\"deletedEnabled\":true,"
                        + "\"originMask\":-1}");
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(72, 1, null, "", 0, 0);
        trigger.loadWiredData(stored, room);

        assertEquals(WiredTriggerVariableChanged.MODE_SELECTED_VARIABLE, trigger.getMode());
        assertTrue(trigger.getPickedBoxIds().isEmpty());
        assertTrue(trigger.matches(trigger, changed(room, 88, 0, WiredVariableChangeOrigin.WIRED)));
        // Every origin, so a change from another room is heard too.
        assertTrue(trigger.matches(trigger, changed(room, 88, 0, WiredVariableChangeOrigin.OTHER_ROOM)));
        assertFalse(trigger.matches(trigger, changed(room, 88, 1, WiredVariableChangeOrigin.WIRED)));
    }

    @Test
    void anotherRoomIsOfficialBitOne() {
        int otherRoom = 1 << WiredVariableChangeOrigin.OTHER_ROOM;
        int wired = 1 << WiredVariableChangeOrigin.WIRED;

        assertEquals(0b0010, WiredTriggerVariableChanged.toOfficialOrigins(otherRoom));
        assertEquals(otherRoom, WiredTriggerVariableChanged.fromOfficialOrigins(0b0010));
        assertEquals(otherRoom | wired, WiredTriggerVariableChanged.fromOfficialOrigins(0b0011));
        // A stored mask keeps the bit through normalising.
        assertEquals(otherRoom | wired, WiredVariableChangeOrigin.normalizeMask(otherRoom | wired));
        assertTrue(WiredVariableChangeOrigin.accepts(otherRoom, WiredVariableChangeOrigin.OTHER_ROOM));
        assertFalse(WiredVariableChangeOrigin.accepts(wired, WiredVariableChangeOrigin.OTHER_ROOM));
    }

    @Test
    void onlyThisRoomOriginIgnoresAChangeFromAnotherRoom() {
        Room room = mock(Room.class);
        // Mode 0 on custom:88, user target, origins: this room only (official bit 0).
        WiredSettings settings = new WiredSettings(new int[] {1, 1, 1, 7, 0b0001, 0, 1}, "custom:88", new int[0], 0);
        WiredTriggerVariableChanged trigger = new WiredTriggerVariableChanged(73, 1, null, "", 0, 0);
        GameEnvironment environment = mock(GameEnvironment.class);
        RoomManager rooms = mock(RoomManager.class);
        when(environment.getRoomManager()).thenReturn(rooms);
        when(rooms.getRoom(anyInt())).thenReturn(room);
        com.eu.habbo.habbohotel.rooms.RoomUserVariableManager users =
                mock(com.eu.habbo.habbohotel.rooms.RoomUserVariableManager.class);
        when(room.getUserVariableManager()).thenReturn(users);
        when(users.getDefinitionInfo(88))
                .thenReturn(mock(com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo.class));
        try (var emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
            assertTrue(trigger.saveData(settings));
        }

        assertTrue(trigger.matches(trigger, changed(room, 88, 0, WiredVariableChangeOrigin.WIRED)));
        assertFalse(trigger.matches(trigger, changed(room, 88, 0, WiredVariableChangeOrigin.OTHER_ROOM)));
    }
}
