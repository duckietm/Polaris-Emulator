package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomSpecialTypesRollerCompatibilityTest {

    @Test
    void publicRollerMapRemainsLiveStableAndMutable() {
        RoomSpecialTypes specialTypes = new RoomSpecialTypes();
        Map<Integer, InteractionRoller> rollers = specialTypes.getRollers();

        rollers.put(17, null);

        assertSame(rollers, specialTypes.getRollers());
        assertTrue(specialTypes.getRollers().containsKey(17));
        rollers.remove(17);
    }

    @Test
    void internalSnapshotDoesNotChangeWithTheLiveMap() {
        RoomSpecialTypes specialTypes = new RoomSpecialTypes();
        Map<Integer, InteractionRoller> rollers = specialTypes.getRollers();
        rollers.put(17, null);

        Map<Integer, InteractionRoller> snapshot = specialTypes.rollerSnapshot();
        rollers.remove(17);

        assertTrue(snapshot.containsKey(17));
        assertFalse(rollers.containsKey(17));
    }

    @Test
    void disposeUsesTheRollerMapMonitor() throws Exception {
        RoomSpecialTypes specialTypes = new RoomSpecialTypes();
        Map<Integer, InteractionRoller> rollers = specialTypes.getRollers();
        CountDownLatch disposeAttempted = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> dispose;

        try {
            synchronized (rollers) {
                dispose = executor.submit(() -> {
                    disposeAttempted.countDown();
                    specialTypes.dispose();
                });

                assertTrue(disposeAttempted.await(2, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class,
                        () -> dispose.get(100, TimeUnit.MILLISECONDS),
                        "dispose must wait for first-party roller mutation to leave the shared monitor");
            }

            dispose.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }
}
