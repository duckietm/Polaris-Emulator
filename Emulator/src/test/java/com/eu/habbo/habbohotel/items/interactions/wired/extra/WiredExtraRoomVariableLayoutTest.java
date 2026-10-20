package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredVariableCatalog;
import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/** AIR's global variable sends {@code [availability]}; its current value goes in the wired context (type 3). */
class WiredExtraRoomVariableLayoutTest {

    private static WiredExtraRoomVariable loaded() throws Exception {
        WiredExtraRoomVariable box = new WiredExtraRoomVariable(41, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn("{\"variableName\":\"round\",\"availability\":10}");
        box.loadWiredData(set, null);
        return box;
    }

    @Test
    void onlyTheAvailabilityIsAnIntParam() throws Exception {
        LegacyDefinition definition = LegacyDefinition.of(loaded(), null);

        assertEquals("round", definition.stringParam());
        assertArrayEquals(new int[] {10}, definition.intParams());
        // Delay and conflicts only: no context without a room.
        assertEquals(8, definition.trailing().length);
    }

    @Test
    void theCurrentValueTravelsInTheGlobalVariableContext() throws Exception {
        WiredExtraRoomVariable box = loaded();
        Room room = mock(Room.class);
        RoomVariableManager variables = mock(RoomVariableManager.class);
        when(variables.getCurrentValue(41)).thenReturn(77);
        when(room.getRoomVariableManager()).thenReturn(variables);
        RoomWiredVariableCatalog.Variable round = new RoomWiredVariableCatalog.Variable(
                RoomWiredVariableCatalog.variableId(RoomWiredVariableCatalog.TARGET_ROOM, 41),
                RoomWiredVariableCatalog.TYPE_CREATED,
                "round",
                10,
                RoomWiredVariableCatalog.TARGET_ROOM,
                true,
                false,
                false);

        LegacyDefinition definition;
        try (MockedStatic<RoomWiredVariableCatalog> catalog =
                mockStatic(RoomWiredVariableCatalog.class, Answers.CALLS_REAL_METHODS)) {
            catalog.when(() -> RoomWiredVariableCatalog.variables(room)).thenReturn(List.of(round));
            definition = LegacyDefinition.of(box, room);
        }

        assertArrayEquals(new int[] {10}, definition.intParams());
        ByteBuffer trailing = ByteBuffer.wrap(definition.trailing());
        assertEquals(0, trailing.getInt()); // delay
        assertEquals(0, trailing.getInt()); // conflicts
        assertEquals(1, trailing.getInt()); // one context
        assertEquals(3, trailing.getInt()); // GLOBAL_VARIABLE_INFO_AND_VALUE
        byte[] id = new byte[trailing.getShort()];
        trailing.get(id);
        assertEquals(round.getVariableId(), new String(id, java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0, trailing.getInt()); // variableType: made with a box
        short nameLength = trailing.getShort();
        trailing.position(trailing.position() + nameLength); // name
        assertEquals(10, trailing.getInt()); // availability
        assertEquals(-10, trailing.getInt()); // target: global
        assertEquals(
                77,
                ByteBuffer.wrap(definition.trailing(), definition.trailing().length - 4, 4)
                        .getInt());
    }
}
