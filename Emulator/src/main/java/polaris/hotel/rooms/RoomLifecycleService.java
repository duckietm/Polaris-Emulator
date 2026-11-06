package polaris.hotel.rooms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import polaris.hotel.users.User;

final class RoomLifecycleService {

    private final RoomDirectory directory;
    private final Map<Integer, RoomCategory> categories;
    private final IntPredicate ownerConnected;
    private final Predicate<Room> uncacheAllowed;
    private final Runnable directoryChanged;

    RoomLifecycleService(
            RoomDirectory directory,
            Map<Integer, RoomCategory> categories,
            IntPredicate ownerConnected,
            Predicate<Room> uncacheAllowed,
            Runnable directoryChanged) {
        this.directory = directory;
        this.categories = categories;
        this.ownerConnected = ownerConnected;
        this.uncacheAllowed = uncacheAllowed;
        this.directoryChanged = directoryChanged;
    }

    void unloadRoomsFor(User user) {
        int ownerId = user.getUserInfo().getId();
        for (Room room : this.roomsToUnloadForOwner(ownerId)) {
            if (!this.uncacheAllowed.test(room)) {
                continue;
            }
            room.dispose();
            this.remove(room);
        }
    }

    List<Room> roomsToUnloadForOwner(int ownerId) {
        if (this.directory.indexedRoomCount().get()
                != this.directory.activeRooms().size()) {
            this.directory.reconcileOwnerIndex();
        }

        List<Room> roomsToDispose = new ArrayList<>();
        Set<Integer> roomIds = this.directory.roomsByOwner().get(ownerId);
        if (roomIds == null) {
            return roomsToDispose;
        }

        for (int roomId : new HashSet<>(roomIds)) {
            Room room = this.directory.activeRooms().get(roomId);
            if (room == null || room.getOwnerId() != ownerId) {
                this.directory.removeFromOwner(ownerId, roomId);
                if (room != null) {
                    this.directory.track(room);
                }
                continue;
            }

            RoomCategory category = this.categories.get(room.getCategory());
            if (!room.isPublicRoom()
                    && !room.isStaffPromotedRoom()
                    && room.getUserCount() == 0
                    && (category == null || !category.isPublic())) {
                roomsToDispose.add(room);
            }
        }
        return roomsToDispose;
    }

    void clearInactiveRooms() {
        Set<Room> roomsToDispose = new HashSet<>();
        for (Map.Entry<Integer, Set<Integer>> entry :
                this.directory.roomsByOwner().entrySet()) {
            if (this.ownerConnected.test(entry.getKey())) {
                continue;
            }
            for (int roomId : entry.getValue()) {
                Room room = this.directory.activeRooms().get(roomId);
                if (room != null && !room.isPublicRoom() && !room.isStaffPromotedRoom() && room.isPreLoaded()) {
                    roomsToDispose.add(room);
                }
            }
        }

        for (Room room : roomsToDispose) {
            room.dispose();
            if (room.getUserCount() == 0) {
                this.remove(room);
            }
        }
    }

    void unload(Room room) {
        room.dispose();
    }

    void uncache(Room room) {
        this.remove(room);
    }

    void quiesceRoomCycles() {
        for (Room room : this.directory.activeRooms().values()) {
            room.quiesceCycleTask();
        }
    }

    void dispose() {
        this.quiesceRoomCycles();
        for (Room room : this.directory.activeRooms().values()) {
            disposeForShutdown(room);
            room.setOwnerChangeListener(null);
        }
        this.directory.clear();
        this.directoryChanged.run();
    }

    /**
     * Unloads a room for the shutdown. A room kept loaded on purpose (a room bundle's model room) returned from
     * dispose at once, and so did one whose RoomUnloadingEvent a plugin cancelled: neither saved its pending items.
     * The lock is lifted here, and a room a plugin still keeps loaded saves its items anyway.
     */
    static void disposeForShutdown(Room room) {
        room.preventUnloading = false;
        room.preventUncaching = false;
        room.dispose();
        if (room.isLoaded()) {
            room.getItemManager().saveAllPendingItems();
        }
    }

    private void remove(Room room) {
        this.directory.untrack(room);
        this.directory.activeRooms().remove(room.getId());
        this.directoryChanged.run();
    }
}
