package polaris.hotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RoomOwnerIndexTest {

    @Test
    void firstPartyRegistrationCachesTheSameRoomAndTracksItsOwner() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = eligibleRoom(41, 7);

        manager.registerActiveRoom(room);

        assertSame(room, activeRooms(manager).get(41));
        assertEquals(Set.of(41), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesUseTheOwnerIndexAndExcludeOtherOwners() {
        RoomManager manager = new RoomManager(false);
        TestRoom ownedRoom = eligibleRoom(41, 7);
        TestRoom otherRoom = eligibleRoom(42, 8);
        manager.registerActiveRoom(ownedRoom);
        manager.registerActiveRoom(otherRoom);
        ownedRoom.resetEligibilityChecks();
        otherRoom.resetEligibilityChecks();

        assertEquals(Set.of(ownedRoom), new HashSet<>(manager.roomsToUnloadForOwner(7)));
        assertEquals(1, ownedRoom.eligibilityChecks());
        assertEquals(0, otherRoom.eligibilityChecks());
    }

    @Test
    void unloadCandidatesFindAndReindexRoomsWhosePublicOwnerWasChanged() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = eligibleRoom(41, 8);
        Room existingRoom = eligibleRoom(42, 7);
        manager.registerActiveRoom(room);
        manager.registerActiveRoom(existingRoom);

        room.setOwnerId(7);

        List<Room> candidates = manager.roomsToUnloadForOwner(7);

        assertEquals(Set.of(room, existingRoom), new HashSet<>(candidates));
        assertFalse(roomsByOwner(manager).getOrDefault(8, Set.of()).contains(41));
        assertEquals(Set.of(41, 42), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesRetainTheLegacyScanAsAFallback() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = eligibleRoom(41, 7);
        activeRooms(manager).put(room.getId(), room);

        assertEquals(List.of(room), manager.roomsToUnloadForOwner(7));
        assertEquals(Set.of(41), roomsByOwner(manager).get(7));
    }

    @Test
    void unloadCandidatesPreserveAllLegacyEligibilityGuards() throws Exception {
        RoomManager manager = new RoomManager(false);
        TestRoom publicRoom = eligibleRoom(41, 7);
        TestRoom promotedRoom = eligibleRoom(42, 7);
        TestRoom occupiedRoom = eligibleRoom(43, 7);
        TestRoom publicCategoryRoom = eligibleRoom(44, 7);
        publicRoom.setPublicRoom(true);
        promotedRoom.setStaffPromotedRoom(true);
        occupiedRoom.setUserCount(1);
        publicCategoryRoom.setCategory(9);
        roomCategories(manager).put(9, publicCategory());

        manager.registerActiveRoom(publicRoom);
        manager.registerActiveRoom(promotedRoom);
        manager.registerActiveRoom(occupiedRoom);
        manager.registerActiveRoom(publicCategoryRoom);

        assertTrue(manager.roomsToUnloadForOwner(7).isEmpty());
    }

    @Test
    void unloadKeepsCancellationAndDisposalOrdering() throws Exception {
        String source = Files.readString(Path.of("src/main/java/polaris/hotel/rooms/RoomLifecycleService.java"));
        int unload = source.indexOf("void unloadRoomsFor");
        int selection = source.indexOf("roomsToUnloadForOwner(ownerId)", unload);
        int callback = source.indexOf("this.uncacheAllowed.test(room)", selection);
        int disposal = source.indexOf("room.dispose()", callback);
        int removal = source.indexOf("this.remove(room)", disposal);

        assertTrue(unload >= 0 && unload < selection);
        assertTrue(selection < callback);
        assertTrue(callback < disposal);
        assertTrue(disposal < removal);
    }

    private static TestRoom eligibleRoom(int id, int ownerId) {
        return new TestRoom(id, ownerId);
    }

    private static RoomCategory publicCategory() throws Exception {
        ResultSet row = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getInt" -> 0;
                    case "getBoolean" -> false;
                    case "getString" -> "public".equals(arguments[0]) ? "1" : "";
                    case "toString" -> "public room category row";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return new RoomCategory(row);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Room> activeRooms(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("activeRooms");
        field.setAccessible(true);
        return (Map<Integer, Room>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, RoomCategory> roomCategories(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("roomCategories");
        field.setAccessible(true);
        return (Map<Integer, RoomCategory>) field.get(manager);
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Set<Integer>> roomsByOwner(RoomManager manager) throws Exception {
        Field field = RoomManager.class.getDeclaredField("roomsByOwner");
        field.setAccessible(true);
        return (Map<Integer, Set<Integer>>) field.get(manager);
    }

    private static final class TestRoom extends Room {
        private int userCount;
        private int eligibilityChecks;

        private TestRoom(int id, int ownerId) {
            super(id, ownerId);
        }

        @Override
        public boolean isPublicRoom() {
            this.eligibilityChecks++;
            return super.isPublicRoom();
        }

        @Override
        public int getUserCount() {
            return this.userCount;
        }

        private void setUserCount(int userCount) {
            this.userCount = userCount;
        }

        private int eligibilityChecks() {
            return this.eligibilityChecks;
        }

        private void resetEligibilityChecks() {
            this.eligibilityChecks = 0;
        }
    }
}
