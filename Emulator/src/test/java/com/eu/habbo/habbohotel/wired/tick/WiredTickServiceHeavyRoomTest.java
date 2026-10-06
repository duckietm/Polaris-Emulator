package com.eu.habbo.habbohotel.wired.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.HeavyWiredRooms;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

/** A room marked heavy runs its wired on the heavy workers, and goes back once it is calm. */
class WiredTickServiceHeavyRoomTest {

    @Test
    void aHeavyRoomsTasksRunOnAHeavyWorker() throws Exception {
        int roomId = 970_101;
        WiredTickService service = new WiredTickService(2, 50, 1);
        service.start();
        try {
            assertEquals("WiredTickShard-1", threadOf(service, roomId));

            HeavyWiredRooms.mark(roomId, true, System.currentTimeMillis());
            assertEquals("WiredTickHeavy-0", threadOf(service, roomId));

            HeavyWiredRooms.forget(roomId);
            assertEquals("WiredTickShard-1", threadOf(service, roomId));
        } finally {
            HeavyWiredRooms.forget(roomId);
            service.stop();
        }
    }

    @Test
    void aHeavyRoomsTimersMoveToAHeavyWorkerAndBack() throws Exception {
        int roomId = 970_103;
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        WiredTickable timer = mock(WiredTickable.class);
        when(timer.getRoomId()).thenReturn(roomId);
        when(timer.getId()).thenReturn(1);

        WiredTickService service = new WiredTickService(2, 10, 1);
        service.start();
        try {
            service.register(room, timer);
            assertEquals(1, service.currentShard(roomId));

            HeavyWiredRooms.mark(roomId, true, System.currentTimeMillis());
            awaitTrue(() -> service.currentShard(roomId) == 2);
            assertEquals(1, service.getTickableCount(roomId));

            HeavyWiredRooms.forget(roomId);
            awaitTrue(() -> service.currentShard(roomId) == 1);
            assertEquals(1, service.getTickableCount(roomId));

            service.unregister(room, timer);
            assertEquals(0, service.getTickableCount(roomId));
        } finally {
            HeavyWiredRooms.forget(roomId);
            service.stop();
        }
    }

    @Test
    void withoutHeavyWorkersAHeavyRoomStaysOnItsWorker() throws Exception {
        int roomId = 970_105;
        WiredTickService service = new WiredTickService(2, 50);
        service.start();
        try {
            HeavyWiredRooms.mark(roomId, true, System.currentTimeMillis());
            assertEquals("WiredTickShard-1", threadOf(service, roomId));
        } finally {
            HeavyWiredRooms.forget(roomId);
            service.stop();
        }
    }

    private static String threadOf(WiredTickService service, int roomId) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> thread = new AtomicReference<>();
        assertTrue(service.executeForRoom(roomId, () -> {
            thread.set(Thread.currentThread().getName());
            done.countDown();
        }));
        assertTrue(done.await(3, TimeUnit.SECONDS));
        return thread.get();
    }

    private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out");
            Thread.sleep(10);
        }
    }
}
