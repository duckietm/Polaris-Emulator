package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredBoxFlashes;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Change Variable Value flashes the definition box of the variable it wrote, also when the value stays the same. */
class WiredEffectChangeVariableValueFlashTest {

    @Test
    void aWriteFlashesTheDefinitionAndItsAddOns() throws Exception {
        Room room = mock(Room.class);
        RoomVariableManager roomVariables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        when(roomVariables.getDefinitionInfo(50)).thenReturn(info);
        when(roomVariables.getCurrentValue(50)).thenReturn(4);
        when(room.getRoomVariableManager()).thenReturn(roomVariables);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);
        InteractionWiredExtra addOn = mock(InteractionWiredExtra.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(50)).thenReturn(definition);
        when(specialTypes.getExtras(anyInt(), anyInt())).thenReturn(new LinkedHashSet<>(List.of(definition, addOn)));

        // Assign 4 to a variable holding 4: the value stays the same, the box still flashes the definition.
        WiredEffectChangeVariableValue box = load(
                room,
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\",\"destinationVariableItemId\":50,"
                        + "\"operation\":0,\"referenceMode\":0,\"referenceConstantValue\":4}");
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredBoxFlashes flashes = mock(WiredBoxFlashes.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getBoxFlashes()).thenReturn(flashes);

        box.execute(ctx);

        // The value stayed: the definition only flips between 0 and 1.
        UnaryOperator<String> next = capturedFlip(flashes, definition);
        assertEquals("0", next.apply("6"));
        assertEquals("1", next.apply("0"));
        verify(addOn).flashBox(room);
    }

    @Test
    void aWriteThatChangesTheValueShowsTheChangeState() throws Exception {
        Room room = mock(Room.class);
        RoomVariableManager roomVariables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        when(roomVariables.getDefinitionInfo(50)).thenReturn(info);
        when(roomVariables.getCurrentValue(50)).thenReturn(3);
        when(room.getRoomVariableManager()).thenReturn(roomVariables);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(50)).thenReturn(definition);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredBoxFlashes flashes = mock(WiredBoxFlashes.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getBoxFlashes()).thenReturn(flashes);
        WiredEffectChangeVariableValue box = load(
                room,
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\",\"destinationVariableItemId\":50,"
                        + "\"operation\":0,\"referenceMode\":0,\"referenceConstantValue\":4}");
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        box.execute(ctx);

        UnaryOperator<String> next = capturedFlip(flashes, definition);
        assertEquals("2", next.apply("6"));
        assertEquals("3", next.apply("2"));
    }

    @Test
    void aChangeThatOnlyLowersShowsTheLowerState() throws Exception {
        Room room = mock(Room.class);
        RoomVariableManager roomVariables = mock(RoomVariableManager.class);
        WiredVariableDefinitionInfo info = mock(WiredVariableDefinitionInfo.class);
        when(info.hasValue()).thenReturn(true);
        when(roomVariables.getDefinitionInfo(50)).thenReturn(info);
        when(roomVariables.getCurrentValue(50)).thenReturn(3);
        when(room.getRoomVariableManager()).thenReturn(roomVariables);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        InteractionWiredExtra definition = mock(InteractionWiredExtra.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getExtra(50)).thenReturn(definition);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredBoxFlashes flashes = mock(WiredBoxFlashes.class);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getBoxFlashes()).thenReturn(flashes);
        WiredEffectChangeVariableValue box = load(
                room,
                "{\"destinationTargetType\":3,\"destinationVariableToken\":\"custom:50\",\"destinationVariableItemId\":50,"
                        + "\"operation\":0,\"referenceMode\":0,\"referenceConstantValue\":2}");
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        box.execute(ctx);

        UnaryOperator<String> next = capturedFlip(flashes, definition);
        assertEquals("4", next.apply("6"));
        assertEquals("5", next.apply("4"));
    }

    @SuppressWarnings("unchecked")
    private static UnaryOperator<String> capturedFlip(WiredBoxFlashes flashes, InteractionWiredExtra box) {
        ArgumentCaptor<UnaryOperator<String>> next = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(flashes).flip(eq(box), next.capture());
        return next.getValue();
    }

    @Test
    void withoutAVariableItDoesNothing() throws Exception {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        WiredEffectChangeVariableValue box =
                load(room, "{\"destinationTargetType\":3,\"destinationVariableToken\":\"\"}");
        WiredContext ctx = mock(WiredContext.class);
        when(ctx.room()).thenReturn(room);

        box.execute(ctx);

        verify(room, never()).getRoomVariableManager();
        verify(specialTypes, never()).getExtra(any(Integer.class));
    }

    private static WiredEffectChangeVariableValue load(Room room, String json) throws Exception {
        WiredEffectChangeVariableValue box = new WiredEffectChangeVariableValue(10, 1, null, "", 0, 0);
        ResultSet data = mock(ResultSet.class);
        when(data.getString("wired_data")).thenReturn(json);
        box.loadWiredData(data, room);
        return box;
    }
}
