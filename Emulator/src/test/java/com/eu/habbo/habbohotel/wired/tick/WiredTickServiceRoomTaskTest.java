package com.eu.habbo.habbohotel.wired.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredTickServiceRoomTaskTest {

    @Test
    void aRoomTaskRunsOnThatRoomsWorker() throws Exception {
        WiredTickService service = new WiredTickService(4, 50);
        service.start();
        try {
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<String> thread = new AtomicReference<>();
            AtomicBoolean onOwnWorker = new AtomicBoolean();
            AtomicBoolean onOtherWorker = new AtomicBoolean(true);

            assertTrue(service.executeForRoom(8735, () -> {
                thread.set(Thread.currentThread().getName());
                onOwnWorker.set(service.isOnRoomWorker(8735));
                onOtherWorker.set(service.isOnRoomWorker(8736));
                done.countDown();
            }));

            assertTrue(done.await(3, TimeUnit.SECONDS));
            assertEquals("WiredTickShard-3", thread.get());
            assertTrue(onOwnWorker.get());
            assertFalse(onOtherWorker.get());
            assertFalse(service.isOnRoomWorker(8735));
        } finally {
            service.stop();
        }
    }

    @Test
    void aFailingTaskDoesNotStopTheWorker() throws Exception {
        WiredTickService service = new WiredTickService(1, 50);
        service.start();
        try {
            CountDownLatch done = new CountDownLatch(1);
            service.executeForRoom(1, () -> {
                throw new IllegalStateException("boom");
            });
            service.executeForRoom(1, done::countDown);

            assertTrue(done.await(3, TimeUnit.SECONDS));
        } finally {
            service.stop();
        }
    }

    @Test
    void aStoppedServiceLetsTheCallerRunTheTask() {
        WiredTickService service = new WiredTickService(2, 50);

        assertFalse(service.executeForRoom(1, () -> {}));
        assertFalse(service.isOnRoomWorker(1));
    }

    @Test
    void aFloodedRoomDropsTasksOverTheLimit() throws Exception {
        WiredTickService service = new WiredTickService(1, 50);
        service.start();
        try {
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch blocked = new CountDownLatch(1);
            service.executeForRoom(1, () -> {
                blocked.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(blocked.await(3, TimeUnit.SECONDS));

            java.util.concurrent.atomic.AtomicInteger ran = new java.util.concurrent.atomic.AtomicInteger();
            for (int i = 0; i < WiredTickService.MAX_PENDING_ROOM_TASKS + 50; i++) {
                assertTrue(service.executeForRoom(1, ran::incrementAndGet));
            }
            CountDownLatch last = new CountDownLatch(1);
            release.countDown();
            service.executeForRoom(2, last::countDown);
            assertTrue(last.await(5, TimeUnit.SECONDS));

            assertTrue(ran.get() < WiredTickService.MAX_PENDING_ROOM_TASKS + 50, "ran " + ran.get());
        } finally {
            service.stop();
        }
    }
}
