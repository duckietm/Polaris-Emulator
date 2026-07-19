package com.eu.habbo.habbohotel.rooms;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
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
