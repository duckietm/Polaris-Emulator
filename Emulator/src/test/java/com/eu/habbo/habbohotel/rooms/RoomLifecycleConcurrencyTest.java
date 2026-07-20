package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoomLifecycleConcurrencyTest {

    private PluginManager originalPluginManager;

    @BeforeEach
    void installPluginManager() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        this.originalPluginManager = (PluginManager) field.get(null);
        field.set(null, new EmptyPluginManager());
    }

    @AfterEach
    void restorePluginManager() throws Exception {
        Field field = Emulator.class.getDeclaredField("pluginManager");
        field.setAccessible(true);
        field.set(null, this.originalPluginManager);
    }

    @Test
    void concurrentCycleAndDisposeCompleteWithoutLockInversion() throws Exception {
        Room room = new Room(41, 7);
        CountDownLatch cycleEntered = new CountDownLatch(1);
        CountDownLatch disposerStarted = new CountDownLatch(1);
        long load = room.beginLoadTransition();
        assertTrue(room.publishLoadTransition(load, FakeScheduledFuture::new));
        setField(room, "cycleManager", new CoordinatedCycleManager(room, cycleEntered, disposerStarted));

        FutureTask<Void> cycle = daemonTask("room-cycle-test", room::run);
        assertTrue(cycleEntered.await(1, TimeUnit.SECONDS));
        FutureTask<Void> dispose = daemonTask("room-dispose-test", () -> {
            disposerStarted.countDown();
            room.dispose();
        });

        cycle.get(2, TimeUnit.SECONDS);
        dispose.get(2, TimeUnit.SECONDS);
    }

    @Test
    void disposeInvalidatesAnInFlightLoadBeforeItCanInstallACycle() throws Exception {
        Room room = new Room(41, 7);
        long load = room.beginLoadTransition();
        FakeScheduledFuture staleTask = new FakeScheduledFuture();

        assertTrue(room.beginUnloadTransition());
        room.finishUnloadTransition();

        AtomicBoolean scheduled = new AtomicBoolean();
        assertFalse(room.publishLoadTransition(load, () -> {
            scheduled.set(true);
            return staleTask;
        }));
        assertFalse(scheduled.get());
        assertFalse(room.isLoadedOrLoading());
    }

    @Test
    void loadPublicationInstallsTheCycleInTheSameTransition() throws Exception {
        Room room = new Room(41, 7);
        long load = room.beginLoadTransition();
        FakeScheduledFuture task = new FakeScheduledFuture();

        assertTrue(room.publishLoadTransition(load, () -> task));

        assertTrue(room.isLoaded());
        assertSame(task, room.roomCycleTask);
    }

    @Test
    void unloadTransitionCancelsThePublishedCycleBeforeClearingLoadedState() throws Exception {
        Room room = new Room(41, 7);
        long load = room.beginLoadTransition();
        FakeScheduledFuture task = new FakeScheduledFuture();
        assertTrue(room.publishLoadTransition(load, () -> task));

        assertTrue(room.beginUnloadTransition());

        assertTrue(task.isCancelled());
        assertFalse(room.isLoaded());
        assertNull(room.roomCycleTask);
    }

    @Test
    void recurringTaskReferenceIsVisibleAcrossLifecycleThreads() throws Exception {
        assertTrue(Modifier.isVolatile(Room.class.getField("roomCycleTask").getModifiers()));
    }

    private static FutureTask<Void> daemonTask(String name, Runnable action) {
        FutureTask<Void> task = new FutureTask<>(action, null);
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    private static void setField(Room room, String name, Object value) throws Exception {
        Field field = Room.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(room, value);
    }

    private static final class CoordinatedCycleManager extends RoomCycleManager {
        private final Room room;
        private final CountDownLatch cycleEntered;
        private final CountDownLatch disposerStarted;

        private CoordinatedCycleManager(Room room, CountDownLatch cycleEntered, CountDownLatch disposerStarted) {
            super(room);
            this.room = room;
            this.cycleEntered = cycleEntered;
            this.disposerStarted = disposerStarted;
        }

        @Override
        public void cycle() {
            this.cycleEntered.countDown();
            try {
                this.disposerStarted.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(100));
            this.room.dispose();
        }
    }

    private static final class EmptyPluginManager extends PluginManager {
        @Override
        public <T extends Event> T fireEvent(T event) {
            return event;
        }
    }

    private static final class FakeScheduledFuture implements ScheduledFuture<Object> {
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
