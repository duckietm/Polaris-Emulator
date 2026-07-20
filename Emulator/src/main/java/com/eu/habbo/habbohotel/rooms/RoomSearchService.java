package com.eu.habbo.habbohotel.rooms;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

final class RoomSearchService {

    private final Supplier<? extends Collection<Room>> rooms;
    private final LongSupplier nanoTime;
    private final long ttlNanos;
    private final Map<SearchKey, CacheEntry> cache = new ConcurrentHashMap<>();

    RoomSearchService(Supplier<? extends Collection<Room>> rooms, Duration ttl) {
        this(rooms, ttl, System::nanoTime);
    }

    RoomSearchService(Supplier<? extends Collection<Room>> rooms, Duration ttl, LongSupplier nanoTime) {
        this.rooms = rooms;
        this.ttlNanos = ttl.toNanos();
        this.nanoTime = nanoTime;
    }

    Set<String> tags() {
        return new TreeSet<>(this.cached(new SearchKey("tags", 0, false), () -> {
            Set<String> tags = new TreeSet<>();
            for (Room room : this.rooms.get()) {
                Collections.addAll(tags, room.getTags().split(";"));
            }
            return List.copyOf(tags);
        }));
    }

    ArrayList<Room> publicRooms() {
        return new ArrayList<>(this.cached(new SearchKey("public", 0, false), () -> {
            ArrayList<Room> result = new ArrayList<>();
            for (Room room : this.rooms.get()) {
                if (room.isPublicRoom()) {
                    result.add(room);
                }
            }
            result.sort(Room.SORT_ID);
            return List.copyOf(result);
        }));
    }

    ArrayList<Room> popularRooms(int count, boolean showPublicRooms) {
        return new ArrayList<>(this.cached(new SearchKey("popular", count, showPublicRooms), () -> {
            ArrayList<Room> result = new ArrayList<>();
            for (Room room : this.rooms.get()) {
                if (room.getUserCount() > 0 && (!room.isPublicRoom() || showPublicRooms)) {
                    result.add(room);
                }
            }
            Collections.sort(result);
            return List.copyOf(result.subList(0, Math.min(result.size(), count)));
        }));
    }

    ArrayList<Room> popularRooms(int count, int category) {
        return new ArrayList<>(this.cached(new SearchKey("popular-category-" + category, count, false), () -> {
            ArrayList<Room> result = new ArrayList<>();
            for (Room room : this.rooms.get()) {
                if (!room.isPublicRoom() && room.getCategory() == category) {
                    result.add(room);
                }
            }
            Collections.sort(result);
            return List.copyOf(result.subList(0, Math.min(result.size(), count)));
        }));
    }

    Map<Integer, List<Room>> popularRoomsByCategory(int count) {
        Map<Integer, List<Room>> cached = this.cached(new SearchKey("popular-by-category", count, false), () -> {
            Map<Integer, List<Room>> grouped = new HashMap<>();
            for (Room room : this.rooms.get()) {
                if (!room.isPublicRoom()) {
                    grouped.computeIfAbsent(room.getCategory(), ignored -> new ArrayList<>())
                            .add(room);
                }
            }

            Map<Integer, List<Room>> result = new HashMap<>();
            for (Map.Entry<Integer, List<Room>> entry : grouped.entrySet()) {
                List<Room> rooms = entry.getValue();
                Collections.sort(rooms);
                result.put(entry.getKey(), List.copyOf(rooms.subList(0, Math.min(rooms.size(), count))));
            }
            return Map.copyOf(result);
        });

        Map<Integer, List<Room>> result = new HashMap<>();
        cached.forEach((category, rooms) -> result.put(category, new ArrayList<>(rooms)));
        return result;
    }

    ArrayList<Room> roomsWithTag(String tag) {
        return new ArrayList<>(this.cached(new SearchKey("tag-" + tag.toLowerCase(), 0, false), () -> {
            ArrayList<Room> result = new ArrayList<>();
            for (Room room : this.rooms.get()) {
                for (String roomTag : room.getTags().split(";")) {
                    if (roomTag.equalsIgnoreCase(tag)) {
                        result.add(room);
                        break;
                    }
                }
            }
            Collections.sort(result);
            return List.copyOf(result);
        }));
    }

    void invalidate() {
        this.cache.clear();
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(SearchKey key, Supplier<T> loader) {
        long now = this.nanoTime.getAsLong();
        CacheEntry existing = this.cache.get(key);
        if (existing != null && now - existing.createdAtNanos() < this.ttlNanos) {
            return (T) existing.value();
        }

        T value = loader.get();
        this.cache.put(key, new CacheEntry(now, value));
        return value;
    }

    private record SearchKey(String kind, int limit, boolean option) {}

    private record CacheEntry(long createdAtNanos, Object value) {}
}
