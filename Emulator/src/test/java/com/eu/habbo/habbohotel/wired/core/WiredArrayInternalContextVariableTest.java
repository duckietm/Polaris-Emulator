package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayCaptureSnapshot;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChangeType;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayEntry;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayFieldDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredVariableDefinitionData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredArrayInternalContextVariableTest {

    @Test
    void exposesExactArrayMutationMetadataAndPreservesLongFieldValues() {
        WiredArrayChange change = WiredArrayChange.field(4, 3, Long.MIN_VALUE + 1, Long.MAX_VALUE, 5, 5);
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, mock(Room.class))
                .arrayChange(change)
                .build();
        WiredContext context = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));

        assertEquals(
                (long) WiredArrayChangeType.FIELD_VALUE_CHANGED,
                WiredInternalVariableSupport.readContextLongValue(context, "@array.change_type"));
        assertEquals(4L, WiredInternalVariableSupport.readContextLongValue(context, "@array.index"));
        assertEquals(-1L, WiredInternalVariableSupport.readContextLongValue(context, "@array.source_index"));
        assertEquals(-1L, WiredInternalVariableSupport.readContextLongValue(context, "@array.destination_index"));
        assertEquals(3L, WiredInternalVariableSupport.readContextLongValue(context, "@array.field_index"));
        assertEquals(
                Long.MIN_VALUE + 1, WiredInternalVariableSupport.readContextLongValue(context, "@array.old_value"));
        assertEquals(Long.MAX_VALUE, WiredInternalVariableSupport.readContextLongValue(context, "@array.new_value"));
        assertEquals(5L, WiredInternalVariableSupport.readContextLongValue(context, "@array.old_length"));
        assertEquals(5L, WiredInternalVariableSupport.readContextLongValue(context, "@array.new_length"));

        assertEquals(Integer.MAX_VALUE, WiredInternalVariableSupport.readContextValue(context, "@array.new_value"));
    }

    @Test
    void exposesCapturedFieldsThroughOrdinaryContextReferences() {
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, mock(Room.class))
                .build();
        WiredContext context = new WiredContext(event, null, mock(WiredServices.class), new WiredState(100));
        WiredArrayDefinition definition = definition();
        context.contextVariables()
                .publishArrayCapture(
                        "Inventory",
                        WiredArrayCaptureSnapshot.found(
                                definition, 3, 4, WiredArrayEntry.fromValues(definition, Map.of(1, Long.MAX_VALUE))));

        assertEquals(Long.MAX_VALUE, WiredInternalVariableSupport.readContextLongValue(context, "inventory.quantity"));
        assertEquals(1L, WiredInternalVariableSupport.readContextLongValue(context, "@array.inventory.found"));
        assertEquals(3L, WiredInternalVariableSupport.readContextLongValue(context, "@array.inventory.index"));
    }

    private static WiredArrayDefinition definition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "record";
        data.arrayMode = "list";
        data.maxEntries = 4;
        data.nextFieldId = 2;
        data.schemaVersion = 1;
        data.fields = List.of(new WiredArrayFieldDefinition(1, "Quantity", 0));
        return WiredArrayDefinition.fromData(data, 4);
    }
}
