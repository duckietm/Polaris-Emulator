package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RoomItemStateTest {

    @Test
    void nullItemStateUpdateIsIgnored() {
        Room room = new Room(41, 7);

        assertDoesNotThrow(() -> room.updateItemState(null));
    }
}
