package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class RoomDirectoryCharacterizationTest {

    @Test
    void registrationPreservesRoomIdentityForLegacyLookup() {
        RoomManager manager = new RoomManager(false);
        Room room = new Room(41, 7);

        manager.registerActiveRoom(room);

        assertSame(room, manager.getRoom(41));
    }

    @Test
    void activeRoomListRemainsADetachedMutableSnapshot() {
        RoomManager manager = new RoomManager(false);
        Room room = new Room(41, 7);
        manager.registerActiveRoom(room);

        ArrayList<Room> snapshot = manager.getActiveRooms();
        snapshot.clear();

        assertEquals(0, snapshot.size());
        assertSame(room, manager.getRoom(41));
    }

    @Test
    void theFirstRegistrationOfAnIdWins() {
        RoomManager manager = new RoomManager(false);
        Room first = new Room(41, 7);
        Room second = new Room(41, 7);

        assertSame(first, manager.registerActiveRoomIfAbsent(first));
        // Two entries loading the same room together must end up on one instance: the
        // loser used to replace the winner and keep ticking in the background.
        assertSame(first, manager.registerActiveRoomIfAbsent(second));
        assertSame(first, manager.getRoom(41));
    }

    @Test
    void registeringAnIdNobodyHoldsReturnsThatRoom() {
        RoomManager manager = new RoomManager(false);
        Room room = new Room(42, 7);

        assertSame(room, manager.registerActiveRoomIfAbsent(room));
        assertSame(room, manager.getRoom(42));
    }

    @Test
    void uncachingRemovesTheRoomFromLegacyLookup() {
        RoomManager manager = new RoomManager(false);
        Room room = new Room(41, 7);
        manager.registerActiveRoom(room);

        manager.uncacheRoom(room);

        assertNull(manager.getRoom(41));
    }
}
