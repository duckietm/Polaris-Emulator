package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;

class RoomGenerationIdentityTest {

    @Test
    void lifecycleGenerationChangesAcrossUnloadAndReload() {
        RoomLifecycle lifecycle = new RoomLifecycle(new TaskSlot());

        long firstLoad = lifecycle.beginLoad();
        assertEquals(firstLoad, lifecycle.generation());
        assertTrue(lifecycle.prepareLoad(firstLoad));
        assertTrue(lifecycle.publishLoad(firstLoad, () -> mock(ScheduledFuture.class)));

        assertTrue(lifecycle.beginUnload());
        long unloaded = lifecycle.generation();
        assertTrue(unloaded > firstLoad);
        lifecycle.finishUnload();

        long secondLoad = lifecycle.beginLoad();
        assertTrue(secondLoad > unloaded);
        assertEquals(secondLoad, lifecycle.generation());
    }

    @Test
    void itemIncarnationNeverReusesTokenForRecycledId() {
        Room room = mock(Room.class);
        when(room.getRoomSpecialTypes()).thenReturn(mock(RoomSpecialTypes.class));
        RoomItemIndex index = new RoomItemIndex(room);
        HabboItem original = item(7_001);
        HabboItem replacement = item(7_001);

        long first = index.registerIncarnation(original);
        assertEquals(first, index.itemIncarnation(7_001));
        index.unregisterIncarnation(original);
        assertEquals(0L, index.itemIncarnation(7_001));

        long second = index.registerIncarnation(replacement);
        assertTrue(second > first);
        index.clear();
        long afterClear = index.registerIncarnation(original);
        assertTrue(afterClear > second);
    }

    private static HabboItem item(int id) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        return item;
    }

    private static final class TaskSlot implements RoomLifecycle.CycleTaskSlot {
        private ScheduledFuture<?> task;

        @Override
        public ScheduledFuture<?> get() {
            return this.task;
        }

        @Override
        public void set(ScheduledFuture<?> task) {
            this.task = task;
        }
    }
}
