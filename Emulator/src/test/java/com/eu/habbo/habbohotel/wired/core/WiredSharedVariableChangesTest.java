package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableReference;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which boxes stand for a shared variable, in the room that owns it and in the rooms that reference it. */
class WiredSharedVariableChangesTest {

    private static Room room(int id, InteractionWiredExtra... extras) {
        Room room = mock(Room.class);
        RoomSpecialTypes types = mock(RoomSpecialTypes.class);
        when(room.getId()).thenReturn(id);
        when(room.getRoomSpecialTypes()).thenReturn(types);
        Set<InteractionWiredExtra> set = new LinkedHashSet<>(List.of(extras));
        when(types.getExtras()).thenReturn(set);
        return room;
    }

    private static WiredExtraVariableReference reference(int id, int sourceRoom, int sourceVariable, boolean user) {
        WiredExtraVariableReference reference = mock(WiredExtraVariableReference.class);
        when(reference.getId()).thenReturn(id);
        when(reference.getSourceRoomId()).thenReturn(sourceRoom);
        when(reference.getSourceVariableItemId()).thenReturn(sourceVariable);
        when(reference.isUserReference()).thenReturn(user);
        return reference;
    }

    @Test
    void aSharedVariableAndItsReferencesNameTheSameSource() {
        WiredExtraUserVariable shared = mock(WiredExtraUserVariable.class);
        when(shared.getId()).thenReturn(10);
        when(shared.isSharedAvailability()).thenReturn(true);
        Room owner = room(1, shared);

        WiredSharedVariableChanges.SharedSource source = WiredSharedVariableChanges.sourceOf(owner, shared);
        assertEquals(new WiredSharedVariableChanges.SharedSource(1, 10, true), source);
        assertEquals(source, WiredSharedVariableChanges.sourceOf(room(2), reference(20, 1, 10, true)));

        // The owner hears it on the variable, a referencing room on its reference.
        assertEquals(List.of(10), WiredSharedVariableChanges.localIds(owner, source));
        Room other = room(2, reference(20, 1, 10, true), reference(21, 1, 11, true), reference(22, 1, 10, false));
        assertEquals(List.of(20), WiredSharedVariableChanges.localIds(other, source));
    }

    @Test
    void aVariableThatIsNotSharedIsNotPassedOn() {
        WiredExtraUserVariable local = mock(WiredExtraUserVariable.class);
        when(local.getId()).thenReturn(10);
        WiredExtraRoomVariable roomLocal = mock(WiredExtraRoomVariable.class);
        when(roomLocal.getId()).thenReturn(12);

        assertNull(WiredSharedVariableChanges.sourceOf(room(1, local), local));
        assertNull(WiredSharedVariableChanges.sourceOf(room(1, roomLocal), roomLocal));
        assertNull(WiredSharedVariableChanges.sourceOf(room(1), null));
    }

    @Test
    void whatTheStacksOfARelayedChangeWriteIsNotRelayedAgain() {
        WiredExtraUserVariable shared = mock(WiredExtraUserVariable.class);
        when(shared.getId()).thenReturn(10);
        when(shared.isSharedAvailability()).thenReturn(true);
        Room owner = room(1, shared);

        WiredSharedVariableChanges.relaying(() -> {
            assertNull(WiredSharedVariableChanges.sourceOf(owner, shared));
            assertNull(WiredSharedVariableChanges.sourceOf(owner, reference(20, 1, 10, true)));
        });
        // Outside a relayed change the shared variable is passed on again.
        assertEquals(
                new WiredSharedVariableChanges.SharedSource(1, 10, true),
                WiredSharedVariableChanges.sourceOf(owner, shared));
    }

    @Test
    void aSharedRoomVariableIsHeardOnRoomReferences() {
        WiredExtraRoomVariable shared = mock(WiredExtraRoomVariable.class);
        when(shared.getId()).thenReturn(30);
        when(shared.isSharedAvailability()).thenReturn(true);

        WiredSharedVariableChanges.SharedSource source = WiredSharedVariableChanges.sourceOf(room(5, shared), shared);
        assertEquals(new WiredSharedVariableChanges.SharedSource(5, 30, false), source);
        assertEquals(List.of(40), WiredSharedVariableChanges.localIds(room(6, reference(40, 5, 30, false)), source));
    }
}
