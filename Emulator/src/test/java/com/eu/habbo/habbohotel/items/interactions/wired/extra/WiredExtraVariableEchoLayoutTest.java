package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredContextVariableSupport;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;
import com.eu.habbo.habbohotel.wired.core.WiredServices;
import com.eu.habbo.habbohotel.wired.core.WiredState;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/** AIR's echo variable: string the name, no ints, variable ids {@code [source]}; a context echo reads its built-in. */
class WiredExtraVariableEchoLayoutTest {

    private static WiredExtraVariableEcho loaded(String wiredData) throws Exception {
        WiredExtraVariableEcho echo = new WiredExtraVariableEcho(61, 1, mock(Item.class), "", 0, 0);
        ResultSet set = mock(ResultSet.class);
        when(set.getString("wired_data")).thenReturn(wiredData);
        echo.loadWiredData(set, null);
        return echo;
    }

    @Test
    void anOldBoxGoesOutInAirsLayout() throws Exception {
        WiredExtraVariableEcho custom = loaded(
                "{\"variableName\":\"lvl\",\"sourceTargetType\":0,"
                        + "\"sourceVariableToken\":\"custom:12\",\"sourceVariableItemId\":12,\"sourceVariableName\":\"score.level\"}");

        LegacyDefinition definition = LegacyDefinition.of(custom, null);
        assertEquals("lvl", definition.stringParam());
        assertArrayEquals(new int[0], definition.intParams());
        // The variable list's id: <target>:<box id>, Habbo's id for a built-in.
        assertArrayEquals(new String[] {"user:12"}, custom.officialVariableIds());

        WiredExtraVariableEcho builtIn = loaded("{\"variableName\":\"x\",\"sourceTargetType\":1,"
                + "\"sourceVariableToken\":\"internal:@position.x\",\"sourceVariableItemId\":0}");
        assertArrayEquals(new String[] {"-120"}, builtIn.officialVariableIds());
    }

    @Test
    void theEditorsSourceIdNamesItsTarget() {
        WiredExtraVariableEcho.ConfigData context =
                WiredExtraVariableEcho.readEditor(null, "turn_type", new String[] {"ctx:@chat_type"});
        assertEquals("turn_type", context.variableName);
        assertEquals(WiredExtraVariableEcho.TARGET_CONTEXT, context.sourceTargetType);
        assertEquals("internal:@chat_type", context.sourceVariableToken);

        WiredExtraVariableEcho.ConfigData global =
                WiredExtraVariableEcho.readEditor(null, "n", new String[] {"room:@furni_count"});
        assertEquals(WiredExtraVariableEcho.TARGET_ROOM, global.sourceTargetType);

        WiredExtraVariableEcho.ConfigData custom = WiredExtraVariableEcho.readEditor(null, "n", new String[] {"12"});
        assertEquals("custom:12", custom.sourceVariableToken);
        assertEquals(12, custom.sourceVariableItemId);

        WiredExtraVariableEcho.ConfigData legacy = WiredExtraVariableEcho.readEditor(
                null,
                "{\"variableName\":\"a\",\"sourceTargetType\":1,\"sourceVariableToken\":\"internal:@state\"}",
                null);
        assertEquals("a", legacy.variableName);
        assertEquals(WiredExtraVariableEcho.TARGET_FURNI, legacy.sourceTargetType);

        assertEquals("-450", WiredExtraVariableEcho.officialSourceId(2, "internal:@chat_type"));
        assertEquals("-230", WiredExtraVariableEcho.officialSourceId(0, "internal:@position_x"));
        assertEquals("furni:@opacity", WiredExtraVariableEcho.officialSourceId(1, "internal:@opacity"));

        // Habbo's ids and the variable list's box ids name their target.
        WiredExtraVariableEcho.ConfigData habbo = WiredExtraVariableEcho.readEditor(null, "n", new String[] {"-450"});
        assertEquals(WiredExtraVariableEcho.TARGET_CONTEXT, habbo.sourceTargetType);
        assertEquals("internal:@chat_type", habbo.sourceVariableToken);
        WiredExtraVariableEcho.ConfigData listed =
                WiredExtraVariableEcho.readEditor(null, "n", new String[] {"furni:12"});
        assertEquals(WiredExtraVariableEcho.TARGET_FURNI, listed.sourceTargetType);
        assertEquals("custom:12", listed.sourceVariableToken);
    }

    @Test
    void aContextEchoReadsItsBuiltInInTheRunningStack() throws Exception {
        WiredExtraVariableEcho echo = loaded("{\"variableName\":\"chat\",\"sourceTargetType\":2,"
                + "\"sourceVariableToken\":\"internal:@chat_type\",\"sourceVariableItemId\":0}");
        assertTrue(echo.isContextEcho());

        Room room = mock(Room.class);
        RoomSpecialTypes special = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(special);
        when(special.getExtra(61)).thenReturn(echo);
        WiredContext ctx = new WiredContext(
                WiredEvent.builder(WiredEvent.Type.CUSTOM, room).chatType(1).build(),
                null,
                mock(WiredServices.class),
                new WiredState(20));

        assertTrue(WiredContextVariableSupport.hasVariable(ctx, 61));
        assertEquals(1, WiredContextVariableSupport.getCurrentValue(ctx, 61));
        // Outside a stack, and in the furni / user / room lists, it is nothing.
        assertFalse(echo.hasVariable(room, 5));
        assertNull(echo.readContextValue(null));
    }
}
