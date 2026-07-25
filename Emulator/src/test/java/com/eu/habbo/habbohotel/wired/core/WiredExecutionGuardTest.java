package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WiredExecutionGuardTest {

    @Test
    void recursionAdmissionRecoversAndPreservesEntryKindDiagnostics() {
        Room room = room(71);
        List<WiredExecutionGuard.EntryKind> blockedKinds = new ArrayList<>();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(1, 100, 1_000L, 2_000L),
                () -> 1_000L,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> blockedKinds.add(kind));

        assertTrue(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));
        assertFalse(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));
        assertEquals(List.of(WiredExecutionGuard.EntryKind.EVENT), blockedKinds);
        assertEquals(1, guard.recursionDepth(room.getId()));
        assertTrue(guard.snapshot(room.getId()).getLogs().stream()
                .anyMatch(entry -> entry.getType() == WiredRoomDiagnostics.Type.RECURSION_TIMEOUT));

        guard.exit(room.getId());
        assertEquals(0, guard.recursionDepth(room.getId()));
        assertTrue(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.SOURCE_ITEM));
        assertFalse(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.SOURCE_ITEM));
        assertEquals(
                List.of(WiredExecutionGuard.EntryKind.EVENT, WiredExecutionGuard.EntryKind.SOURCE_ITEM), blockedKinds);
    }

    @Test
    void rateLimitBansOnceThenExpiresAgainstInjectedClock() {
        Room room = room(72);
        AtomicLong clock = new AtomicLong(1_000L);
        List<Boolean> banDecisions = new ArrayList<>();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(10, 2, 1_000L, 2_000L),
                clock::get,
                (ignoredRoom, eventType, count, limits, banned) -> banDecisions.add(banned),
                (ignoredRoom, eventType, kind, depth, maximum) -> {});

        assertTrue(enterAndExit(guard, room));
        assertTrue(enterAndExit(guard, room));
        assertFalse(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));
        assertFalse(guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));
        assertEquals(List.of(true), banDecisions);
        assertTrue(guard.snapshot(room.getId()).getKilledRemainingSeconds() > 0);

        clock.set(3_001L);
        assertTrue(enterAndExit(guard, room));
        assertEquals(0, guard.snapshot(room.getId()).getKilledRemainingSeconds());
    }

    @Test
    void hotAdmissionCachesRemainIsolatedByRoomEventAndCleanup() {
        Room firstRoom = room(75);
        Room secondRoom = room(76);
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(10, 1, 1_000L, 0L),
                () -> 1_000L,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});

        assertTrue(enterAndExit(guard, firstRoom));
        assertTrue(enterAndExit(guard, secondRoom));
        assertTrue(enterAndExit(guard, firstRoom, WiredEvent.Type.USER_SAYS));
        assertFalse(guard.tryEnter(firstRoom, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));

        guard.clearRoomRateLimiters(firstRoom.getId());
        assertTrue(enterAndExit(guard, firstRoom));

        assertTrue(guard.tryEnter(firstRoom, WiredEvent.Type.SIGNAL_RECEIVED, WiredExecutionGuard.EntryKind.EVENT));
        assertTrue(guard.tryEnter(secondRoom, WiredEvent.Type.SIGNAL_RECEIVED, WiredExecutionGuard.EntryKind.EVENT));
        assertEquals(1, guard.recursionDepth(firstRoom.getId()));
        assertEquals(1, guard.recursionDepth(secondRoom.getId()));

        guard.clearRoomRecursionDepth(firstRoom.getId());
        assertEquals(0, guard.recursionDepth(firstRoom.getId()));
        assertEquals(1, guard.recursionDepth(secondRoom.getId()));
        guard.exit(secondRoom.getId());
        guard.exit(firstRoom.getId());
        assertEquals(0, guard.recursionDepth(secondRoom.getId()));
    }

    @Test
    void deferredAdmissionPublishesOnlyWhenStackExecutionBegins() {
        Room room = room(77);
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(10, 100, 1_000L, 0L),
                () -> 1_000L,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});

        assertTrue(guard.tryEnterDeferredPublication(
                room.getId(), room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT));
        assertEquals(1, guard.currentChainDepth(room.getId()));
        assertEquals(0, guard.recursionDepth(room.getId()));

        guard.publishDeferredAdmission(room.getId());
        assertEquals(1, guard.recursionDepth(room.getId()));
        guard.exitDeferredAdmission(room.getId(), true);

        assertEquals(0, guard.currentChainDepth(room.getId()));
        assertEquals(0, guard.recursionDepth(room.getId()));
    }

    @Test
    void concurrentFirstWindowCountsEveryAdmissionExactlyOnce() throws Exception {
        Room room = room(73);
        AtomicInteger limitSignals = new AtomicInteger();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(20, 8, 1_000L, 0L),
                () -> 1_000L,
                (ignoredRoom, eventType, count, limits, banned) -> limitSignals.incrementAndGet(),
                (ignoredRoom, eventType, kind, depth, maximum) -> {});
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return enterAndExit(guard, room);
                }));
            }

            ready.await();
            start.countDown();
            assertEquals(
                    8,
                    results.stream().filter(WiredExecutionGuardTest::accepted).count());
        }

        assertEquals(1, limitSignals.get());
    }

    @Test
    void unrelatedConcurrentChainsDoNotConsumeEachOthersRecursionBudget() throws Exception {
        Room room = room(74);
        AtomicInteger admitted = new AtomicInteger();
        WiredExecutionGuard guard = new WiredExecutionGuard(
                limits(1, 100, 1_000L, 0L),
                () -> 1_000L,
                (ignoredRoom, eventType, count, limits, banned) -> {},
                (ignoredRoom, eventType, kind, depth, maximum) -> {});
        CountDownLatch ready = new CountDownLatch(16);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch attempted = new CountDownLatch(16);
        CountDownLatch release = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(16)) {
            List<Future<?>> results = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    boolean entered = guard.tryEnter(room, WiredEvent.Type.CUSTOM, WiredExecutionGuard.EntryKind.EVENT);
                    if (entered) {
                        admitted.incrementAndGet();
                    }
                    attempted.countDown();
                    release.await();
                    if (entered) {
                        guard.exit(room.getId());
                    }
                    return null;
                }));
            }

            ready.await();
            start.countDown();
            attempted.await();
            try {
                assertEquals(16, admitted.get());
                assertEquals(1, guard.recursionDepth(room.getId()));
            } finally {
                release.countDown();
            }
            for (Future<?> result : results) {
                result.get();
            }
        }

        assertEquals(0, guard.recursionDepth(room.getId()));
    }

    private static boolean enterAndExit(WiredExecutionGuard guard, Room room) {
        return enterAndExit(guard, room, WiredEvent.Type.CUSTOM);
    }

    private static boolean enterAndExit(WiredExecutionGuard guard, Room room, WiredEvent.Type eventType) {
        boolean entered = guard.tryEnter(room, eventType, WiredExecutionGuard.EntryKind.EVENT);
        if (entered) {
            guard.exit(room.getId());
        }
        return entered;
    }

    private static boolean accepted(Future<Boolean> result) {
        try {
            return result.get();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static Room room(int roomId) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(roomId);
        return room;
    }

    private static WiredExecutionGuard.Limits limits(
            int recursionDepth, int eventLimit, long rateWindowMs, long banDurationMs) {
        return new WiredExecutionGuard.Limits(
                recursionDepth, eventLimit, rateWindowMs, banDurationMs, 1_000, 100, 10, 50, 150, 70, 5, 2, 60);
    }
}
