package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredArrayTextConnectorTest {
    @Test
    void supportsLongMappingsAndLegacySimpleArrayFieldZero() throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(Long.MAX_VALUE + "=Maximum");
        WiredExtraVariableTextConnector connector =
                new WiredExtraVariableTextConnector(1, 1, mock(Item.class), "", 0, 0);
        connector.loadWiredData(set, null);
        WiredArrayVariableDefinition simple = mock(WiredArrayVariableDefinition.class);
        when(simple.isArray()).thenReturn(true);
        when(simple.getArrayDefinition()).thenReturn(simpleDefinition());

        assertEquals("Maximum", connector.resolveText(Long.MAX_VALUE));
        assertTrue(connector.appliesToField(simple, WiredArrayDefinition.SIMPLE_VALUE_FIELD_ID));
        assertFalse(connector.appliesToField(simple, 2));
    }

    private static WiredArrayDefinition simpleDefinition() {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.valueShape = "array";
        data.arrayFormat = "simple";
        data.arrayMode = "list";
        data.maxEntries = 8;
        data.schemaVersion = 1;
        data.fields = List.of();
        return WiredArrayDefinition.fromData(data, 8);
    }
}
