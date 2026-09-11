package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTextOutputVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import java.sql.ResultSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WiredArrayTextOutputTest {
    @Test
    void selectedRecordFieldRetainsTextualModeWhenReopenedAndOnlyThatFieldIsMarkedConnected() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        WiredExtraRoomVariable definition = mock(WiredExtraRoomVariable.class);
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.name = "records";
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.maxEntries = 8;
        data.nextFieldId = 3;
        data.fields =
                List.of(new WiredArrayFieldDefinition(1, "number", 0), new WiredArrayFieldDefinition(2, "label", 1));
        WiredArrayDefinition array = WiredArrayDefinition.fromData(data, 8);
        when(definition.getId()).thenReturn(12);
        when(definition.getVariableName()).thenReturn("records");
        when(definition.getArrayVariableType()).thenReturn(WiredArrayVariableType.ROOM);
        when(definition.getArrayDefinition()).thenReturn(array);
        when(definition.isArray()).thenReturn(true);
        when(definition.isArrayWritable()).thenReturn(true);
        when(special.getExtra(12)).thenReturn(definition);
        when(special.getExtras()).thenReturn(Set.of(definition));
        WiredExtraVariableTextConnector connector =
                new WiredExtraVariableTextConnector(13, 1, mock(Item.class), "", 0, 0);
        ResultSet connectorData = mock(ResultSet.class);
        when(connectorData.getString("wired_data")).thenReturn("{\"mappingsText\":\"7=Seven\",\"fieldId\":2}");
        connector.loadWiredData(connectorData, room);
        when(special.getExtras(0, 0)).thenReturn(Set.of(definition, connector));

        WiredExtraTextOutputVariable connected = loadOutput(room, 2);
        assertEquals(WiredExtraTextOutputVariable.DISPLAY_TEXTUAL, connected.getDisplayType(room));
        ResultSet saved = mock(ResultSet.class);
        when(saved.getString("wired_data")).thenReturn(connected.getWiredData());
        WiredExtraTextOutputVariable reopened = new WiredExtraTextOutputVariable(15, 1, mock(Item.class), "", 0, 0);
        reopened.loadWiredData(saved, room);
        assertEquals(WiredExtraTextOutputVariable.DISPLAY_TEXTUAL, reopened.getDisplayType(room));
        assertEquals(
                WiredExtraTextOutputVariable.DISPLAY_NUMERIC,
                loadOutput(room, 1).getDisplayType(room));

        WiredArrayDefinitionSupport.EditorDefinition editor =
                WiredArrayDefinitionSupport.collect(room).getFirst();
        assertFalse(editor.fields().getFirst().textConnected());
        assertTrue(editor.fields().getLast().textConnected());
        assertTrue(editor.writable());
    }

    private static WiredExtraTextOutputVariable loadOutput(Room room, int fieldId) throws Exception {
        WiredExtraTextOutputVariable output = new WiredExtraTextOutputVariable(14, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data"))
                .thenReturn(
                        "{\"targetType\":3,\"variableToken\":\"custom:12\",\"displayType\":2,\"arrayAddress\":{\"fieldId\":"
                                + fieldId + "}}");
        output.loadWiredData(set, room);
        return output;
    }
}
