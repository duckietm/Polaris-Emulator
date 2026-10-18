package com.eu.habbo.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import org.junit.jupiter.api.Test;

class ModToolRoomLookupTest {
    @Test
    void anActiveRoomIsUsedAsItIs() {
        RoomManager manager = mock(RoomManager.class);
        Room room = mock(Room.class);
        when(manager.getRoom(12)).thenReturn(room);

        assertSame(room, ModToolRoomLookup.find(manager, 12));
        verify(manager, never()).loadRoom(anyInt());
    }

    @Test
    void aRoomNobodyIsInIsLoadedSoTheToolStillAnswers() {
        RoomManager manager = mock(RoomManager.class);
        Room room = mock(Room.class);
        when(manager.getRoom(12)).thenReturn(null);
        when(manager.loadRoom(12)).thenReturn(room);

        assertSame(room, ModToolRoomLookup.find(manager, 12));
    }

    @Test
    void anInvalidIdLoadsNothing() {
        RoomManager manager = mock(RoomManager.class);

        assertNull(ModToolRoomLookup.find(manager, 0));
        assertNull(ModToolRoomLookup.find(manager, -3));
        verify(manager, never()).loadRoom(anyInt());
    }
}
