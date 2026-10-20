package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** AIR's reference variable: string the name, ints {@code [readOnly]}, variable ids {@code [shared variable]}. */
class WiredExtraVariableReferenceLayoutTest {

    private static WiredExtraVariableReference loaded(String wiredData) throws Exception {
        WiredExtraVariableReference reference = new WiredExtraVariableReference(71, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        reference.loadWiredData(set, null);
        return reference;
    }

    private static String readString(ByteBuffer buffer) {
        byte[] bytes = new byte[buffer.getShort()];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Test
    void anOldBoxGoesOutInAirsLayoutWithItsSourceListed() throws Exception {
        WiredExtraVariableReference reference = loaded("{\"variableName\":\"coins\",\"sourceRoomId\":5,"
                + "\"sourceRoomName\":\"Bank\",\"sourceVariableItemId\":9,\"sourceVariableName\":\"coins\","
                + "\"sourceTargetType\":0,\"hasValue\":true,\"readOnly\":false}");

        LegacyDefinition definition = LegacyDefinition.of(reference, null);
        assertEquals("coins", definition.stringParam());
        assertArrayEquals(new int[] {0}, definition.intParams());
        assertArrayEquals(new String[] {"user:9"}, reference.officialVariableIds());

        ByteBuffer trailing = ByteBuffer.wrap(definition.trailing());
        trailing.getInt(); // delay
        trailing.getInt(); // conflicts
        assertEquals(1, trailing.getInt());
        assertEquals(4, trailing.getInt()); // SHARED_VARIABLE_LIST
        assertEquals(1, trailing.getInt()); // the source, though no longer listed as shared
        assertEquals(5, trailing.getInt());
        assertEquals("Bank", readString(trailing));
        assertEquals("user:9", readString(trailing));
    }

    @Test
    void theEditorsSharedIdAndReadOnlyAreRead() {
        WiredExtraVariableReference.ConfigData config = WiredExtraVariableReference.readEditor(
                null, new WiredSettings(new int[] {0}, "round", new int[0], 0) {
                    @Override
                    public String[] getVariableIds() {
                        return new String[] {"room:11"};
                    }
                });

        assertEquals("round", config.variableName);
        assertFalse(config.readOnly);
        assertEquals(11, config.sourceVariableItemId);
        assertEquals(WiredVariableReferenceSupport.TARGET_ROOM, config.sourceTargetType);

        WiredExtraVariableReference.ConfigData legacy = WiredExtraVariableReference.readEditor(
                null,
                new WiredSettings(
                        new int[0], "{\"variableName\":\"s\",\"sourceRoomId\":5,\"sourceVariableItemId\":9}", new int[0], 0));
        assertEquals(5, legacy.sourceRoomId);
        assertTrue(legacy.readOnly);
        assertEquals("user:9", WiredExtraVariableReference.sharedVariableId(WiredVariableReferenceSupport.TARGET_USER, 9));
    }
}
