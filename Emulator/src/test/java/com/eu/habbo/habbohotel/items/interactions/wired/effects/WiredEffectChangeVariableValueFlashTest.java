package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomVariableManager;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import java.sql.ResultSet;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

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

        box.execute(ctx);

        verify(definition).flashBox(room);
        verify(addOn).flashBox(room);
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
