package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class RoomItemStateTest {

    @Test
    void nullItemStateUpdateIsIgnored() {
        Room room = new Room(41, 7);

        assertDoesNotThrow(() -> room.updateItemState(null));
    }
}
