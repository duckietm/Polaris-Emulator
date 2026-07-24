package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredTrigger;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomSpecialTypes;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RoomWiredStackIndexConcurrencyTest {

    @Test
    void aNewRoomLifecycleNeverReusesThePreviousGenerationSnapshot() {
        AtomicLong lifecycleGeneration = new AtomicLong(1L);
        AtomicLong cacheGeneration = new AtomicLong(1L);
        AtomicReference<Set<InteractionWiredTrigger>> triggers = new AtomicReference<>();
        Room room = room(91, lifecycleGeneration, cacheGeneration, triggers, null, null);
        InteractionWiredTrigger oldTrigger = trigger(901);
        InteractionWiredTrigger newTrigger = trigger(902);
        triggers.set(Set.of(oldTrigger));
        RoomWiredStackIndex index = new RoomWiredStackIndex(true);

        assertEquals(901, onlyTriggerId(index.getStacks(room, WiredEvent.Type.CUSTOM)));

        lifecycleGeneration.incrementAndGet();
        triggers.set(Set.of(newTrigger));

        assertEquals(902, onlyTriggerId(index.getStacks(room, WiredEvent.Type.CUSTOM)));
        assertEquals(1, index.getCachedRoomCount());
    }

    @Test
    void invalidationDuringBuildRetriesInsteadOfReturningTheCrossMutationSnapshot() throws Exception {
        AtomicLong lifecycleGeneration = new AtomicLong(10L);
        AtomicLong cacheGeneration = new AtomicLong(20L);
        AtomicReference<Set<InteractionWiredTrigger>> triggers = new AtomicReference<>();
        CountDownLatch buildStarted = new CountDownLatch(1);
        CountDownLatch releaseBuild = new CountDownLatch(1);
        Room room = room(92, lifecycleGeneration, cacheGeneration, triggers, buildStarted, releaseBuild);
        InteractionWiredTrigger oldTrigger = trigger(911);
        InteractionWiredTrigger newTrigger = trigger(912);
        triggers.set(Set.of(oldTrigger));
        RoomWiredStackIndex index = new RoomWiredStackIndex(true);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            var result = executor.submit(() -> index.getStacks(room, WiredEvent.Type.CUSTOM));
            assertTrue(buildStarted.await(5, TimeUnit.SECONDS));

            cacheGeneration.incrementAndGet();
            triggers.set(Set.of(newTrigger));
            index.invalidateAll(room);
            releaseBuild.countDown();

            assertEquals(912, onlyTriggerId(result.get(5, TimeUnit.SECONDS)));
            assertEquals(912, onlyTriggerId(index.getStacks(room, WiredEvent.Type.CUSTOM)));
        } finally {
            releaseBuild.countDown();
            executor.shutdownNow();
        }
    }

    private static Room room(
            int id,
            AtomicLong lifecycleGeneration,
            AtomicLong cacheGeneration,
            AtomicReference<Set<InteractionWiredTrigger>> triggers,
            CountDownLatch buildStarted,
            CountDownLatch releaseBuild) {
        Room room = mock(Room.class);
        RoomSpecialTypes specialTypes = mock(RoomSpecialTypes.class);
        AtomicBoolean firstBuild = new AtomicBoolean(true);
        when(room.getId()).thenReturn(id);
        when(room.getLifecycleGeneration()).thenAnswer(ignored -> lifecycleGeneration.get());
        when(room.getWiredCacheGeneration()).thenAnswer(ignored -> cacheGeneration.get());
        when(room.getRoomSpecialTypes()).thenReturn(specialTypes);
        when(specialTypes.getTriggers(any(WiredTriggerType.class))).thenAnswer(ignored -> {
            Set<InteractionWiredTrigger> captured = triggers.get();
            if (buildStarted != null && firstBuild.compareAndSet(true, false)) {
                buildStarted.countDown();
                if (!releaseBuild.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("fixture build release timed out");
                }
            }
            return captured;
        });
        when(specialTypes.getConditions(anyShort(), anyShort())).thenReturn(java.util.Set.of());
        when(specialTypes.getEffects(anyShort(), anyShort())).thenReturn(java.util.Set.of());
        when(specialTypes.getExtras(anyShort(), anyShort())).thenReturn(java.util.Set.of());
        return room;
    }

    private static InteractionWiredTrigger trigger(int id) {
        InteractionWiredTrigger trigger = mock(InteractionWiredTrigger.class);
        when(trigger.getId()).thenReturn(id);
        when(trigger.getX()).thenReturn((short) 1);
        when(trigger.getY()).thenReturn((short) 1);
        return trigger;
    }

    private static int onlyTriggerId(List<WiredStack> stacks) {
        assertEquals(1, stacks.size());
        return stacks.getFirst().triggerItem().getId();
    }
}
