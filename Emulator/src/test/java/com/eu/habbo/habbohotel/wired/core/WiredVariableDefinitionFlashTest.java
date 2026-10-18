package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.rooms.RoomWiredRuntime;
import com.eu.habbo.habbohotel.rooms.WiredBoxFlashes;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The state a variable definition box shows after each kind of write. */
class WiredVariableDefinitionFlashTest {

    @Test
    void aDefinitionShowsTheKindOfItsLastWriteAndAlternatesWithinIt() {
        assertEquals("6", WiredVariableDefinitionFlash.nextState("0", WiredVariableDefinitionFlash.KIND_GIVEN));
        assertEquals("7", WiredVariableDefinitionFlash.nextState("6", WiredVariableDefinitionFlash.KIND_GIVEN));
        assertEquals("6", WiredVariableDefinitionFlash.nextState("7", WiredVariableDefinitionFlash.KIND_GIVEN));
        assertEquals("2", WiredVariableDefinitionFlash.nextState("7", WiredVariableDefinitionFlash.KIND_CHANGED));
        assertEquals("3", WiredVariableDefinitionFlash.nextState("2", WiredVariableDefinitionFlash.KIND_CHANGED));
        assertEquals("8", WiredVariableDefinitionFlash.nextState("2", WiredVariableDefinitionFlash.KIND_REMOVED));
        assertEquals("9", WiredVariableDefinitionFlash.nextState("8", WiredVariableDefinitionFlash.KIND_REMOVED));
        // A change that kept the value: give shows 6, then 0, then 1.
        assertEquals("0", WiredVariableDefinitionFlash.nextState("6", WiredVariableDefinitionFlash.KIND_UNCHANGED));
        assertEquals("1", WiredVariableDefinitionFlash.nextState("0", WiredVariableDefinitionFlash.KIND_UNCHANGED));
    }

    @Test
    void aWriteThroughAnEchoShowsOnItsSourceAndOnlyFlipsTheEcho() {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        RoomWiredRuntime runtime = mock(RoomWiredRuntime.class);
        WiredBoxFlashes flashes = mock(WiredBoxFlashes.class);
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(room.getWiredRuntime()).thenReturn(runtime);
        when(runtime.getBoxFlashes()).thenReturn(flashes);
        WiredExtraVariableEcho echo = mock(WiredExtraVariableEcho.class);
        when(echo.getSourceVariableItemId()).thenReturn(30);
        InteractionWiredExtra source = mock(InteractionWiredExtra.class);
        when(specialTypes.getExtra(20)).thenReturn(echo);
        when(specialTypes.getExtra(30)).thenReturn(source);
        when(specialTypes.getExtras(anyIntValue(), anyIntValue())).thenReturn(Set.of(source));

        WiredVariableDefinitionFlash.flash(room, 20, WiredVariableDefinitionFlash.KIND_CHANGED);

        verify(echo).flashBox(room);
        verify(flashes, never()).flip(eq(echo), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UnaryOperator<String>> next = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(flashes).flip(eq(source), next.capture());
        assertEquals("2", next.getValue().apply("0"));
    }

    private static int anyIntValue() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}
