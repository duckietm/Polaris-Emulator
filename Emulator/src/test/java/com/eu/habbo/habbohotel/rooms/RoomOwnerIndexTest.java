package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoomOwnerIndexTest {

    @Test
    void firstPartyRegistrationCachesTheSameRoomAndTracksItsOwner() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(41);
        when(room.getOwnerId()).thenReturn(7);

        manager.registerActiveRoom(room);

        assertSame(room, activeRooms(manager).get(41));
        assertEquals(Set.of(41), roomsByOwner(manager).get(7));
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Room> activeRooms(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("activeRooms");
        field.setAccessible(true);
        return (Map<Integer, Room>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Set<Integer>> roomsByOwner(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("roomsByOwner");
        field.setAccessible(true);
        return (Map<Integer, Set<Integer>>) field.get(manager);
    }
}
