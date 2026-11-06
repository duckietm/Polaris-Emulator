package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomSearchCharacterizationTest {

    @Test
    void tagsRemainSortedAndDeduplicatedAcrossActiveRooms() {
        RoomManager manager = new RoomManager(false);
        Room first = room(41, false, 0, "retro;polaris");
        Room second = room(42, false, 0, "polaris;java");
        manager.registerActiveRoom(first);
        manager.registerActiveRoom(second);

        assertEquals(List.of("java", "polaris", "retro"), List.copyOf(manager.getTags()));
    }

    @Test
    void publicRoomsRemainSortedByDescendingRoomId() {
        RoomManager manager = new RoomManager(false);
        Room lower = room(41, true, 0, "");
        Room higher = room(42, true, 0, "");
        manager.registerActiveRoom(lower);
        manager.registerActiveRoom(higher);

        assertEquals(List.of(higher, lower), manager.getPublicRooms());
    }

    @Test
    void popularRoomsExcludeEmptyAndPublicRoomsByDefault() {
        RoomManager manager = new RoomManager(false);
        TestRoom occupied = room(41, false, 1, "");
        TestRoom empty = room(42, false, 0, "");
        TestRoom publicRoom = room(43, true, 1, "");
        manager.registerActiveRoom(occupied);
        manager.registerActiveRoom(empty);
        manager.registerActiveRoom(publicRoom);
        boolean previous = RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB;

        try {
            RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB = false;
            assertEquals(List.of(occupied), manager.getPopularRooms(10));
        } finally {
            RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB = previous;
        }
    }

    @Test
    void tagSearchMatchesWholeTagsWithoutCaseSensitivity() {
        RoomManager manager = new RoomManager(false);
        Room matching = room(41, false, 0, "retro;polaris");
        Room partial = room(42, false, 0, "retrospective");
        manager.registerActiveRoom(matching);
        manager.registerActiveRoom(partial);

        assertEquals(Set.of(matching), Set.copyOf(manager.getRoomsWithTag("RETRO")));
    }

    private static TestRoom room(int id, boolean publicRoom, int users, String tags) {
        TestRoom room = new TestRoom(id, users);
        room.setPublicRoom(publicRoom);
        room.setTags(tags);
        return room;
    }

    private static final class TestRoom extends Room {
        private final int users;

        private TestRoom(int id, int users) {
            super(id, 7);
            this.users = users;
        }

        @Override
        public int getUserCount() {
            return this.users;
        }
    }
}
