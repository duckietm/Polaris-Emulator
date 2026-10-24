package com.eu.habbo.habbohotel.rooms;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

final class RoomDirectory {

    private final ConcurrentHashMap<Integer, Room> activeRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, Set<Integer>> roomsByOwner = new ConcurrentHashMap<>();
    private final AtomicInteger indexedRoomCount = new AtomicInteger();

    ConcurrentHashMap<Integer, Room> activeRooms() {
        return this.activeRooms;
    }

    ConcurrentHashMap<Integer, Set<Integer>> roomsByOwner() {
        return this.roomsByOwner;
    }

    AtomicInteger indexedRoomCount() {
        return this.indexedRoomCount;
    }

    void register(Room room) {
        Room previousRoom = this.activeRooms.put(room.getId(), room);
        if (previousRoom != null) {
            this.untrack(previousRoom);
        }

        room.setOwnerChangeListener(this::ownerChanged);
        this.track(room);
    }

    /**
     * Registers a room unless an instance of that id is already active.
     *
     * <p>{@link #register} replaces whatever was there, which is what a reload wants but
     * not what two threads loading the same room want: the loser's instance stays alive,
     * keeps ticking and keeps saving state nobody can see.
     *
     * @return the active instance - the one just registered, or the one already there
     */
    Room registerIfAbsent(Room room) {
        Room existing = this.activeRooms.putIfAbsent(room.getId(), room);
        if (existing != null) {
            return existing;
        }

        room.setOwnerChangeListener(this::ownerChanged);
        this.track(room);
        return room;
    }

    void track(Room room) {
        if (this.roomsByOwner
                .computeIfAbsent(room.getOwnerId(), ignored -> ConcurrentHashMap.newKeySet())
                .add(room.getId())) {
            this.indexedRoomCount.incrementAndGet();
        }
    }

    void untrack(Room room) {
        room.setOwnerChangeListener(null);
        this.removeFromOwner(room.getOwnerId(), room.getId());
    }

    void removeFromOwner(int ownerId, int roomId) {
        Set<Integer> rooms = this.roomsByOwner.get(ownerId);
        if (rooms == null) {
            return;
        }
        if (rooms.remove(roomId)) {
            this.indexedRoomCount.decrementAndGet();
        }
        if (rooms.isEmpty()) {
            this.roomsByOwner.remove(ownerId, rooms);
        }
    }

    void reconcileOwnerIndex() {
        for (Map.Entry<Integer, Set<Integer>> entry : this.roomsByOwner.entrySet()) {
            int indexedOwnerId = entry.getKey();
            for (int roomId : new HashSet<>(entry.getValue())) {
                Room room = this.activeRooms.get(roomId);
                if (room == null || room.getOwnerId() != indexedOwnerId) {
                    this.removeFromOwner(indexedOwnerId, roomId);
                }
            }
        }

        for (Room room : this.activeRooms.values()) {
            room.setOwnerChangeListener(this::ownerChanged);
            this.track(room);
        }
    }

    void clear() {
        for (Room room : this.activeRooms.values()) {
            room.setOwnerChangeListener(null);
        }
        this.roomsByOwner.clear();
        this.indexedRoomCount.set(0);
        this.activeRooms.clear();
    }

    private void ownerChanged(Room room, int previousOwnerId) {
        if (this.activeRooms.get(room.getId()) != room) {
            return;
        }
        this.removeFromOwner(previousOwnerId, room.getId());
        this.track(room);
    }
}
