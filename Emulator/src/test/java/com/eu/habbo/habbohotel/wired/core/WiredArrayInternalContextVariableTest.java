package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChange;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayChangeType;
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
}
