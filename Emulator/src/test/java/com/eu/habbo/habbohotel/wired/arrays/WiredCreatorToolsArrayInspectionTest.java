package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomArrayVariableManager;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredCreatorToolsArrayInspectionTest {
    @Test
    void boundsPagesAndKeepsLongValuesAsStrings() {
        WiredArrayDefinition array = definition();
        WiredArrayValue value = WiredArrayValue.empty(array, 64);
        value.apply(WiredArrayStructuralOperation.APPEND, 0, 0, Map.of(1, Long.MAX_VALUE, 2, 3L));
        Room room = mock(Room.class);
        RoomArrayVariableManager manager = mock(RoomArrayVariableManager.class);
        WiredArrayVariableDefinition variable = mock(WiredArrayVariableDefinition.class);
        when(room.getId()).thenReturn(44);
        when(room.getArrayVariableManager()).thenReturn(manager);
        when(variable.getId()).thenReturn(91);
        when(variable.getVariableName()).thenReturn("Inventory");
        when(variable.isArray()).thenReturn(true);
        when(variable.isArrayWritable()).thenReturn(true);
        when(variable.getArrayVariableType()).thenReturn(WiredArrayVariableType.USER);
        when(variable.getArrayDefinition()).thenReturn(array);
        when(variable.getArrayStorageRoomId(44)).thenReturn(44);
        when(variable.getArrayStorageDefinitionItemId()).thenReturn(91);
        when(manager.getValue(variable, 501)).thenReturn(value);

        WiredCreatorToolsArrayInspection result =
                WiredCreatorToolsArrayInspection.create(room, "user", 12, variable, 501, 99, 10_000);

        assertEquals(50, result.pageSize);
        assertEquals(1, result.pageCount);
        assertEquals(0, result.page);
        assertEquals("9223372036854775807", result.entries.getFirst().values.get("1"));
        assertTrue(result.definition.writable);
    }

    private static WiredArrayDefinition definition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.arrayMode = "list";
        data.maxEntries = 8;
        data.nextFieldId = 3;
        data.schemaVersion = 1;
        data.fields =
                List.of(new WiredArrayFieldDefinition(1, "ItemID", 0), new WiredArrayFieldDefinition(2, "Quantity", 1));
        return WiredArrayDefinition.fromData(data, 8);
    }
}
