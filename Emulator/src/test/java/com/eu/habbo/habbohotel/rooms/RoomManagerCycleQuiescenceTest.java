package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomManagerCycleQuiescenceTest {

    @Test
    void everyRecurringRoomCycleIsCancelledBeforeDisposalStarts() {
        RoomManager manager = new RoomManager(false);
        Room first = new Room(41, 7);
        Room second = new Room(42, 8);
        TestScheduledFuture firstCycle = new TestScheduledFuture();
        TestScheduledFuture secondCycle = new TestScheduledFuture();
        first.roomCycleTask = firstCycle;
        second.roomCycleTask = secondCycle;
        manager.registerActiveRoom(first);
        manager.registerActiveRoom(second);

        manager.quiesceRoomCycles();

        assertTrue(firstCycle.isCancelled());
        assertTrue(secondCycle.isCancelled());
        assertNull(first.roomCycleTask);
        assertNull(second.roomCycleTask);
    }

    @Test
    void quiescenceWaitsForAnAlreadyRunningCycle() throws Exception {
        RoomManager manager = new RoomManager(false);
        Room room = new Room(41, 7);
        CountDownLatch cycleStarted = new CountDownLatch(1);
        CountDownLatch releaseCycle = new CountDownLatch(1);
        long load = room.beginLoadTransition();
        assertTrue(room.publishLoadTransition(
                load,
                TestScheduledFuture::new));
        setField(room, "cycleManager", new BlockingCycleManager(
                room,
                cycleStarted,
                releaseCycle
        ));
        manager.registerActiveRoom(room);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> cycle = executor.submit(room);
            assertTrue(cycleStarted.await(1, TimeUnit.SECONDS));
            Future<?> quiesce = executor.submit(manager::quiesceRoomCycles);

            assertThrows(
                    TimeoutException.class,
                    () -> quiesce.get(100, TimeUnit.MILLISECONDS)
            );
            releaseCycle.countDown();
            cycle.get(1, TimeUnit.SECONDS);
            quiesce.get(1, TimeUnit.SECONDS);
        } finally {
            releaseCycle.countDown();
            executor.shutdownNow();
        }
    }

    private static void setField(Room room, String name, Object value) throws Exception {
        Field field = Room.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(room, value);
    }

    private static final class BlockingCycleManager extends RoomCycleManager {
        private final CountDownLatch started;
        private final CountDownLatch release;

        private BlockingCycleManager(
                Room room,
                CountDownLatch started,
                CountDownLatch release
        ) {
            super(room);
            this.started = started;
            this.release = release;
        }

        @Override
        public void cycle() {
            this.started.countDown();
            try {
                this.release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object> {
        private volatile boolean cancelled;

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            this.cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return this.cancelled;
        }

        @Override
        public boolean isDone() {
            return this.cancelled;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
