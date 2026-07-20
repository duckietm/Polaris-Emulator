package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RoomSearchServiceCacheTest {

    @Test
    void popularSearchRefreshesAfterTheShortTtl() {
        ArrayList<Room> rooms = new ArrayList<>();
        AtomicLong time = new AtomicLong();
        RoomSearchService service = new RoomSearchService(() -> rooms, Duration.ofSeconds(1), time::get);
        Room first = occupiedRoom(41);
        Room second = occupiedRoom(42);
        rooms.add(first);

        assertEquals(List.of(first), service.popularRooms(10, false));
        rooms.add(second);
        assertEquals(List.of(first), service.popularRooms(10, false));

        time.addAndGet(Duration.ofSeconds(1).toNanos());

        assertEquals(Set.of(first, second), Set.copyOf(service.popularRooms(10, false)));
    }

    @Test
    void invalidationPublishesDirectoryChangesImmediately() {
        ArrayList<Room> rooms = new ArrayList<>();
        RoomSearchService service = new RoomSearchService(() -> rooms, Duration.ofSeconds(1), () -> 0);
        Room first = occupiedRoom(41);
        Room second = occupiedRoom(42);
        rooms.add(first);
        service.popularRooms(10, false);
        rooms.add(second);

        service.invalidate();

        assertEquals(Set.of(first, second), Set.copyOf(service.popularRooms(10, false)));
    }

    @Test
    void callersCannotMutateCachedLists() {
        Room room = occupiedRoom(41);
        RoomSearchService service = new RoomSearchService(() -> List.of(room), Duration.ofSeconds(1), () -> 0);

        ArrayList<Room> result = service.popularRooms(10, false);
        result.clear();

        assertEquals(List.of(room), service.popularRooms(10, false));
    }

    private static Room occupiedRoom(int id) {
        return new Room(id, 7) {
            @Override
            public int getUserCount() {
                return 1;
            }
        };
    }
}
