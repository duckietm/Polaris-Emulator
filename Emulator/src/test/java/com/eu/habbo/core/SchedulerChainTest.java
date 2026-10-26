package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.eu.habbo.Emulator;
import com.eu.habbo.threading.ThreadPooling;
import java.util.ArrayDeque;
import java.util.Deque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * A currency scheduler pays every online user once per interval. Each tick schedules
 * the next and used to stop only by reading {@code disposed}, so turning the scheduler
 * off and on inside one interval left the queued tick of the old chain to wake up
 * enabled and carry on beside the new one - paying twice per interval, for good.
 */
class SchedulerChainTest {

    /** Counts its ticks; a real scheduler pays the online users here. */
    private static final class CountingScheduler extends Scheduler {
        private int ticks;

        private CountingScheduler() {
            super(10);
        }

        @Override
        protected void tick() {
            this.ticks++;
        }
    }

    private final Deque<Runnable> queue = new ArrayDeque<>();
    private MockedStatic<Emulator> emulator;

    @BeforeEach
    void stubThreading() {
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            this.queue.add(i.getArgument(0));
            return null;
        });

        this.emulator = mockStatic(Emulator.class);
        this.emulator.when(Emulator::getThreading).thenReturn(threading);
    }

    @AfterEach
    void close() {
        this.emulator.close();
    }

    @Test
    void restartingWithinAnIntervalLeavesOneChain() {
        CountingScheduler scheduler = new CountingScheduler();

        scheduler.restart();
        assertEquals(1, this.queue.size());

        // Disabled and enabled again before the queued tick fires, which is what
        // :update_config does.
        scheduler.setDisposed(true);
        scheduler.restart();

        // The tick of the replaced chain first, then the live one.
        this.queue.remove().run();
        assertEquals(0, scheduler.ticks, "the replaced chain must not pay");

        this.queue.remove().run();
        assertEquals(1, scheduler.ticks);
        assertEquals(1, this.queue.size(), "exactly one tick stays in flight");
    }

    @Test
    void aDisabledSchedulerStopsPayingAndStopsRescheduling() {
        CountingScheduler scheduler = new CountingScheduler();
        scheduler.restart();

        scheduler.setDisposed(true);
        this.queue.remove().run();

        assertEquals(0, scheduler.ticks);
        assertTrue(this.queue.isEmpty(), "a disabled scheduler queues nothing further");
        assertTrue(scheduler.isDisposed());
    }

    @Test
    void theLiveChainKeepsTicking() {
        CountingScheduler scheduler = new CountingScheduler();
        scheduler.restart();

        for (int tick = 1; tick <= 3; tick++) {
            this.queue.remove().run();
            assertEquals(tick, scheduler.ticks);
            assertEquals(1, this.queue.size());
        }

        assertFalse(scheduler.isDisposed());
    }
}
