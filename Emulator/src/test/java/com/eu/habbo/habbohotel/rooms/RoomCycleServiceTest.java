package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RoomCycleServiceTest {

    @Test
    void aRoomAlwaysRunsOnTheSameWorker() {
        RoomCycleService service = new RoomCycleService(4, 250);
        try {
            assertEquals(4, service.workerCount());
            assertEquals(service.workerOf(8735), service.workerOf(8735));
            assertEquals(3, service.workerOf(8735));
            assertNotEquals(service.workerOf(1), service.workerOf(2));
            assertEquals(3, service.workerOf(-1));
        } finally {
            service.dispose();
        }
    }

    @Test
    void theWorkerCountStaysInBounds() {
        RoomCycleService none = new RoomCycleService(0, 250);
        RoomCycleService many = new RoomCycleService(1_000, 250);
        try {
            assertEquals(1, none.workerCount());
            assertEquals(RoomCycleService.MAX_WORKERS, many.workerCount());
            assertTrue(RoomCycleService.defaultWorkers() >= 2 && RoomCycleService.defaultWorkers() <= 16);
        } finally {
            none.dispose();
            many.dispose();
        }
    }

    @Test
    void aFailingCycleNeverStopsTheRoom() {
        RoomCycleService service = new RoomCycleService(1, 250);
        try {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(5);
            doThrow(new IllegalStateException("boom")).when(room).run();
            assertDoesNotThrow(() -> service.runCycle(room));

            doThrow(new StackOverflowError()).when(room).run();
            assertDoesNotThrow(() -> service.runCycle(room));
        } finally {
            service.dispose();
        }
    }

    @Test
    void aScheduledRoomCyclesOnItsOwnWorkerUntilCancelled() throws Exception {
        RoomCycleService service = new RoomCycleService(2, 250);
        try {
            Room room = mock(Room.class);
            when(room.getId()).thenReturn(7);
            CountDownLatch cycles = new CountDownLatch(2);
            AtomicReference<String> thread = new AtomicReference<>();
            doAnswer(invocation -> {
                        thread.set(Thread.currentThread().getName());
                        cycles.countDown();
                        return null;
                    })
                    .when(room)
                    .run();

            ScheduledFuture<?> future = service.schedule(room);

            assertTrue(cycles.await(3, TimeUnit.SECONDS));
            assertTrue(thread.get().startsWith("RoomCycle-1"), thread.get());
            future.cancel(false);
            assertTrue(future.isCancelled());
        } finally {
            service.dispose();
        }
    }
}
