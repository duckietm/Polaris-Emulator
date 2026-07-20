package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RoomUserCountPersistenceTest {

    @Test
    void repeatedChangesQueueOneWriteWithTheLatestCount() {
        AtomicInteger userCount = new AtomicInteger(1);
        List<Integer> persistedCounts = new ArrayList<>();
        List<Runnable> queued = new ArrayList<>();
        RoomUserCountPersistence persistence =
                new RoomUserCountPersistence(userCount::get, persistedCounts::add, queued::add);

        persistence.schedule();
        userCount.set(2);
        persistence.schedule();
        userCount.set(3);
        persistence.schedule();

        assertEquals(1, queued.size());
        queued.removeFirst().run();
        assertEquals(List.of(3), persistedCounts);
    }

    @Test
    void aChangeDuringWriteFlushesAgainBeforeTheWorkerExits() {
        AtomicInteger userCount = new AtomicInteger(1);
        List<Integer> persistedCounts = new ArrayList<>();
        List<Runnable> queued = new ArrayList<>();
        RoomUserCountPersistence[] holder = new RoomUserCountPersistence[1];
        holder[0] = new RoomUserCountPersistence(
                userCount::get,
                count -> {
                    persistedCounts.add(count);
                    if (count == 1) {
                        userCount.set(2);
                        holder[0].schedule();
                    }
                },
                queued::add);

        holder[0].schedule();
        queued.removeFirst().run();

        assertEquals(List.of(1, 2), persistedCounts);
        assertEquals(List.of(), queued);
    }

    @Test
    void aLaterChangeCanScheduleAfterTheWorkerCompletes() {
        AtomicInteger userCount = new AtomicInteger(1);
        List<Integer> persistedCounts = new ArrayList<>();
        List<Runnable> queued = new ArrayList<>();
        RoomUserCountPersistence persistence =
                new RoomUserCountPersistence(userCount::get, persistedCounts::add, queued::add);

        persistence.schedule();
        queued.removeFirst().run();
        userCount.set(0);
        persistence.schedule();
        queued.removeFirst().run();

        assertEquals(List.of(1, 0), persistedCounts);
    }
}
