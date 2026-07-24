package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredStackRepositoryTest {

    @Test
    void preservesSourceFilteringOrderCacheAndRoomScopedInvalidation() {
        Room room42 = room(42);
        Room room420 = room(420);
        WiredStack firstMatch = stack(7);
        WiredStack secondMatch = stack(7);
        WiredStack other = stack(8);
        AtomicInteger indexCalls = new AtomicInteger();
        ConcurrentHashMap<Integer, List<WiredStack>> stacksByRoom = new ConcurrentHashMap<>();
        stacksByRoom.put(42, List.of(firstMatch, other, secondMatch));
        stacksByRoom.put(420, List.of(other));

        WiredStackRepository repository = new WiredStackRepository((room, type) -> {
            indexCalls.incrementAndGet();
            return stacksByRoom.getOrDefault(room.getId(), List.of());
        });

        List<WiredStack> first = repository.getStacksForSourceItem(room42, WiredEvent.Type.TIMER_REPEAT, 7);
        assertEquals(List.of(firstMatch, secondMatch), first);
        assertThrows(UnsupportedOperationException.class, () -> first.add(other));
        assertSame(first, repository.getStacksForSourceItem(room42, WiredEvent.Type.TIMER_REPEAT, 7));
        assertEquals(1, indexCalls.get());

        List<WiredStack> otherRoom = repository.getStacksForSourceItem(room420, WiredEvent.Type.TIMER_REPEAT, 8);
        assertEquals(List.of(other), otherRoom);
        assertEquals(2, indexCalls.get());

        stacksByRoom.put(42, List.of());
        stacksByRoom.put(420, List.of(firstMatch));
        repository.clearRoomSourceStackCache(42);

        assertEquals(List.of(), repository.getStacksForSourceItem(room42, WiredEvent.Type.TIMER_REPEAT, 7));
        assertSame(otherRoom, repository.getStacksForSourceItem(room420, WiredEvent.Type.TIMER_REPEAT, 8));
        assertEquals(3, indexCalls.get());

        repository.clearAllSourceStackCache();
        assertEquals(List.of(), repository.getStacksForSourceItem(room420, WiredEvent.Type.TIMER_REPEAT, 8));
        assertEquals(4, indexCalls.get());
    }

    @Test
    void preservesDirectIndexDelegationWithoutCaching() {
        Room room = room(17);
        WiredStack stack = stack(3);
        AtomicInteger calls = new AtomicInteger();
        WiredStackRepository repository = new WiredStackRepository((ignoredRoom, ignoredType) -> {
            calls.incrementAndGet();
            return List.of(stack);
        });

        assertEquals(List.of(stack), repository.getStacks(room, WiredEvent.Type.CUSTOM));
        assertEquals(List.of(stack), repository.getStacks(room, WiredEvent.Type.CUSTOM));
        assertEquals(2, calls.get());
    }

    @Test
    void sourceCacheIsScopedToTheCurrentRoomAndMutationGeneration() {
        AtomicLong lifecycleGeneration = new AtomicLong(1L);
        AtomicLong cacheGeneration = new AtomicLong(1L);
        Room room = room(18, lifecycleGeneration, cacheGeneration);
        WiredStack oldStack = stack(4);
        WiredStack newStack = stack(4);
        AtomicReference<List<WiredStack>> current = new AtomicReference<>(List.of(oldStack));
        WiredStackRepository repository = new WiredStackRepository((ignoredRoom, ignoredType) -> current.get());

        assertSame(
                oldStack,
                repository
                        .getStacksForSourceItem(room, WiredEvent.Type.CUSTOM, 4)
                        .getFirst());

        lifecycleGeneration.incrementAndGet();
        cacheGeneration.incrementAndGet();
        current.set(List.of(newStack));

        assertSame(
                newStack,
                repository
                        .getStacksForSourceItem(room, WiredEvent.Type.CUSTOM, 4)
                        .getFirst());
        assertEquals(1, repository.sourceStackCacheSize());
    }

    @Test
    void sourceInvalidationDuringBuildCannotRepublishTheOldSnapshot() throws Exception {
        AtomicLong lifecycleGeneration = new AtomicLong(3L);
        AtomicLong cacheGeneration = new AtomicLong(7L);
        Room room = room(19, lifecycleGeneration, cacheGeneration);
        WiredStack oldStack = stack(5);
        WiredStack newStack = stack(5);
        AtomicReference<List<WiredStack>> current = new AtomicReference<>(List.of(oldStack));
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        WiredStackRepository repository = new WiredStackRepository((ignoredRoom, ignoredType) -> {
            List<WiredStack> captured = current.get();
            if (calls.getAndIncrement() == 0) {
                buildStarted.countDown();
                try {
                    if (!releaseBuild.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("fixture build release timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            return captured;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var result = executor.submit(() -> repository.getStacksForSourceItem(room, WiredEvent.Type.CUSTOM, 5));
            org.junit.jupiter.api.Assertions.assertTrue(buildStarted.await(5, TimeUnit.SECONDS));

            cacheGeneration.incrementAndGet();
            current.set(List.of(newStack));
            repository.clearRoomSourceStackCache(room.getId());
            releaseBuild.countDown();

            assertSame(newStack, result.get(5, TimeUnit.SECONDS).getFirst());
            assertSame(
                    newStack,
                    repository
                            .getStacksForSourceItem(room, WiredEvent.Type.CUSTOM, 5)
                            .getFirst());
        } finally {
            releaseBuild.countDown();
            executor.shutdownNow();
        }
    }

    private static Room room(int id) {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(id);
        return room;
    }

    private static Room room(int id, AtomicLong lifecycleGeneration, AtomicLong cacheGeneration) {
        Room room = room(id);
        when(room.getLifecycleGeneration()).thenAnswer(ignored -> lifecycleGeneration.get());
        when(room.getWiredCacheGeneration()).thenAnswer(ignored -> cacheGeneration.get());
        return room;
    }

    private static WiredStack stack(int itemId) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(itemId);
        return new WiredStack(item, null, List.of(), List.of());
    }
}
