package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomUserVariableManager;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** AIR's user variable ints are {@code [availability, hasValue]}; the furni variable keeps {@code [hasValue, availability]}. */
class WiredExtraUserVariableLayoutTest {

    private static WiredExtraUserVariable loaded(String wiredData) throws Exception {
        WiredExtraUserVariable box = new WiredExtraUserVariable(31, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        box.loadWiredData(set, null);
        return box;
    }

    @Test
    void anOldBoxIsSentAvailabilityFirst() throws Exception {
        WiredExtraUserVariable box = loaded("{\"variableName\":\"points\",\"hasValue\":true,\"availability\":10}");

        LegacyDefinition definition = LegacyDefinition.of(box, null);
        assertEquals("points", definition.stringParam());
        assertArrayEquals(new int[] {10, 1}, definition.intParams());
    }

    @Test
    void theEditorsAvailabilityThenHasValueAreSaved() throws Exception {
        WiredExtraUserVariable box = new WiredExtraUserVariable(31, 1, mock(Item.class), "", 0, 0);
        box.setRoomId(9);
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(special.getExtras()).thenReturn(new java.util.LinkedHashSet<>(List.<InteractionWiredExtra>of(box)));
        when(room.getRoomSpecialTypes()).thenReturn(special);
        RoomArrayVariableManager arrays = mock(RoomArrayVariableManager.class);
        doAnswer(invocation -> {
                    ((Runnable) invocation.getArgument(3)).run();
                    return null;
                })
                .when(arrays)
                .updateDefinition(any(), any(), anyBoolean(), any());
        when(room.getArrayVariableManager()).thenReturn(arrays);
        when(room.getUserVariableManager()).thenReturn(mock(RoomUserVariableManager.class));
        RoomManager rooms = mock(RoomManager.class);
        when(rooms.getRoom(9)).thenReturn(room);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getRoomManager()).thenReturn(rooms);

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class)) {
            emulator.when(Emulator::getGameEnvironment).thenReturn(environment);

            box.saveData(new WiredSettings(new int[] {11, 1}, "points", new int[0], 0), null);
            assertEquals(WiredExtraUserVariable.AVAILABILITY_SHARED, box.getAvailability());
            assertTrue(box.hasValue());

            box.saveData(new WiredSettings(new int[] {0, 0}, "points", new int[0], 0), null);
            assertEquals(WiredExtraUserVariable.AVAILABILITY_ROOM, box.getAvailability());
            assertFalse(box.hasValue());
        }

        WiredExtraUserVariable reloaded = loaded(box.getWiredData());
        assertArrayEquals(new int[] {0, 0}, LegacyDefinition.of(reloaded, null).intParams());
    }
}
