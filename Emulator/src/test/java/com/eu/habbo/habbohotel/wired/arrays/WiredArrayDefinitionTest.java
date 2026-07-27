package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredLargePayload;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class WiredArrayDefinitionTest {

    @Test
    void simpleArraysHaveOneStableValueField() {
        WiredVariableDefinitionData data = arrayData("simple", "list", 32);

        WiredArrayDefinition definition = WiredArrayDefinition.fromData(data, 128);

        assertEquals(WiredArrayFormat.SIMPLE, definition.getFormat());
        assertEquals(WiredArrayMode.LIST, definition.getMode());
        assertEquals(32, definition.getMaxEntries());
        assertEquals(List.of(new WiredArrayFieldDefinition(1, "value", 0)), definition.getFields());
        assertEquals(2, definition.getNextFieldId());
    }

    @Test
    void recordFieldIdsSurviveRenamesAndReordering() {
        WiredVariableDefinitionData original = arrayData("record", "slots", 20);
        original.fields =
                List.of(new WiredArrayFieldDefinition(8, "Quantity", 1), new WiredArrayFieldDefinition(3, "ItemID", 0));
        original.nextFieldId = 9;
        WiredVariableDefinitionData renamed = arrayData("record", "slots", 30);
        renamed.fields =
                List.of(new WiredArrayFieldDefinition(3, "Item", 1), new WiredArrayFieldDefinition(8, "Count", 0));
        renamed.nextFieldId = 9;

        WiredArrayDefinition first = WiredArrayDefinition.fromData(original, 128);
        WiredArrayDefinition second = WiredArrayDefinition.fromData(renamed, 128);

        assertEquals("ItemID", first.getField(3).getName());
        assertEquals("Item", second.getField(3).getName());
        assertTrue(first.isShapeCompatible(second));
        assertFalse(first.removesFieldsComparedWith(second));
    }

    @Test
    void rejectsReservedDuplicateAndUnboundedFields() {
        WiredVariableDefinitionData reserved = arrayData("record", "list", 10);
        reserved.fields = List.of(new WiredArrayFieldDefinition(1, "index", 0));
        reserved.nextFieldId = 2;

        WiredVariableDefinitionData duplicate = arrayData("record", "list", 10);
        duplicate.fields =
                List.of(new WiredArrayFieldDefinition(1, "Quality", 0), new WiredArrayFieldDefinition(2, "quality", 1));
        duplicate.nextFieldId = 3;

        WiredVariableDefinitionData oversized = arrayData("record", "list", 10);
        oversized.fields = List.of(new WiredArrayFieldDefinition(WiredArrayDefinition.MAX_FIELD_ID + 1, "Value", 0));
        oversized.nextFieldId = WiredArrayDefinition.MAX_FIELD_ID + 1;

        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(reserved, 128));
        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(duplicate, 128));
        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(oversized, 128));
    }

    @Test
    void enforcesBothDefinitionAndServerCapacity() {
        WiredVariableDefinitionData zero = arrayData("simple", "list", 0);
        WiredVariableDefinitionData aboveServer = arrayData("simple", "list", 129);
        WiredVariableDefinitionData aboveAbsolute = arrayData("simple", "list", 2049);

        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(zero, 128));
        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(aboveServer, 128));
        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(aboveAbsolute, 4096));
    }

    @Test
    void storedDefinitionsRemainLoadableWhenRuntimeMaximumIsLowered() {
        WiredVariableDefinitionData stored = arrayData("simple", "list", 1024);

        assertThrows(IllegalArgumentException.class, () -> WiredArrayDefinition.fromData(stored, 512));
        assertEquals(
                1024,
                WiredArrayDefinitionSupport.parseStoredArrayDefinition(stored).getMaxEntries());
    }

    @Test
    void permanentFurniArraysSurviveALowerRuntimeMaximum() throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data"))
                .thenReturn("{\"variableName\":\"Inventory\",\"hasValue\":true,\"availability\":10,"
                        + "\"definition\":{\"name\":\"Inventory\",\"valueShape\":\"array\","
                        + "\"arrayFormat\":\"simple\",\"arrayMode\":\"list\",\"maxEntries\":1024,"
                        + "\"nextFieldId\":2,\"fields\":[],\"schemaVersion\":1}} ");
        WiredExtraFurniVariable variable = new WiredExtraFurniVariable(42, 1, mock(Item.class), "", 0, 0);

        WiredArraySettings.configure(512, 4096, 50);
        try {
            variable.loadWiredData(row, null);

            assertTrue(variable instanceof WiredLargePayload);
            assertTrue(variable.isArray());
            assertEquals(1024, variable.getArrayDefinition().getMaxEntries());
        } finally {
            WiredArraySettings.configure(
                    WiredArrayDefinition.ABSOLUTE_MAX_ENTRIES,
                    WiredArrayDefinition.DEFAULT_MAX_POPULATED_CELLS,
                    WiredArraySettings.DEFAULT_MAX_OWNERS_PER_EXECUTION);
        }
    }

    @Test
    void acceptsOnlyBoundedArrayCapturePaths() {
        assertTrue(WiredArrayRuntimeSupport.isValidCapturePath("@array.Inventory.ItemID"));
        assertTrue(WiredArrayRuntimeSupport.isValidCapturePath("@array.inventory.found"));
        assertFalse(WiredArrayRuntimeSupport.isValidCapturePath("@array.inventory"));
        assertFalse(WiredArrayRuntimeSupport.isValidCapturePath("@array.inventory.item-id"));
        assertFalse(WiredArrayRuntimeSupport.isValidCapturePath("@array.inventory.$field"));
        assertFalse(WiredArrayRuntimeSupport.isValidCapturePath("@array." + "a".repeat(41) + ".value"));
        assertTrue(WiredArrayRuntimeSupport.isValidCaptureProjectionPath("Inventory.ItemID"));
        assertTrue(WiredArrayRuntimeSupport.isValidCaptureProjectionPath("@array.Inventory.ItemID"));
        assertFalse(WiredArrayRuntimeSupport.isValidCaptureProjectionPath("Inventory.Item.ID"));
    }

    static WiredVariableDefinitionData arrayData(String format, String mode, int maximum) {
        WiredVariableDefinitionData data = new WiredVariableDefinitionData();
        data.name = "Inventory";
        data.valueShape = "array";
        data.arrayFormat = format;
        data.arrayMode = mode;
        data.maxEntries = maximum;
        data.schemaVersion = WiredArrayDefinition.SCHEMA_VERSION;
        return data;
    }
}
