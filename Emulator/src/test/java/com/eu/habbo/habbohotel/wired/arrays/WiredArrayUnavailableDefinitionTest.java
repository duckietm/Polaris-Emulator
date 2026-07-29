package com.eu.habbo.habbohotel.wired.arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A stored array schema this server cannot parse must report as "declared but unavailable" rather
 * than as either a usable array (which would let callers dereference a null schema) or a scalar
 * (which would let a later save delete the builder's stored values).
 */
class WiredArrayUnavailableDefinitionTest {

    private static final String UNREADABLE_SCHEMA = "{\"variableName\":\"Inventory\",\"hasValue\":true,"
            + "\"availability\":10,\"definition\":{\"name\":\"Inventory\",\"valueShape\":\"array\","
            + "\"arrayFormat\":\"simple\",\"arrayMode\":\"list\",\"maxEntries\":128,"
            + "\"nextFieldId\":2,\"fields\":[],\"schemaVersion\":99}}";

    @Test
    void unreadableSchemaIsDeclaredButNotUsable() throws Exception {
        WiredExtraFurniVariable variable = loadFurni(UNREADABLE_SCHEMA);

        assertFalse(variable.isArray(), "an unparsable schema must not claim to be a usable array");
        assertTrue(variable.isArrayUnavailable());
        assertTrue(variable.isArrayDeclared(), "an unparsable array is still not a scalar");
        assertNull(variable.getArrayDefinition());
        assertFalse(variable.hasValue());
    }

    @Test
    void unreadableSchemaIsPreservedOnSaveInsteadOfDegradingToAScalar() throws Exception {
        WiredExtraUserVariable variable = new WiredExtraUserVariable(42, 1, mock(Item.class), "", 0, 0);
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(UNREADABLE_SCHEMA);
        variable.loadWiredData(row, null);

        String persisted = variable.getWiredData();

        assertTrue(persisted.contains("\"schemaVersion\":99"), persisted);
        assertTrue(persisted.contains("\"valueShape\":\"array\""), persisted);
    }

    @Test
    void addressResolutionDoesNotDereferenceAnUnavailableSchema() throws Exception {
        WiredExtraFurniVariable variable = loadFurni(UNREADABLE_SCHEMA);
        WiredArrayAddress address = new WiredArrayAddress();
        address.mode = WiredArrayAddress.CONSTANT;
        address.value = 0L;

        assertNull(WiredArrayRuntimeSupport.resolveIndex(null, List.of(), address, variable, null));
        assertFalse(WiredArrayEditorSupport.isValidAddress(address, variable, null));
    }

    @Test
    void readableSchemaStaysUsable() throws Exception {
        WiredExtraFurniVariable variable =
                loadFurni(UNREADABLE_SCHEMA.replace("\"schemaVersion\":99", "\"schemaVersion\":1"));

        assertTrue(variable.isArray());
        assertFalse(variable.isArrayUnavailable());
        assertTrue(variable.isArrayDeclared());
        assertEquals(128, variable.getArrayDefinition().getMaxEntries());
    }

    private static WiredExtraFurniVariable loadFurni(String wiredData) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("wired_data")).thenReturn(wiredData);
        WiredExtraFurniVariable variable = new WiredExtraFurniVariable(42, 1, mock(Item.class), "", 0, 0);
        variable.loadWiredData(row, null);
        return variable;
    }
}
