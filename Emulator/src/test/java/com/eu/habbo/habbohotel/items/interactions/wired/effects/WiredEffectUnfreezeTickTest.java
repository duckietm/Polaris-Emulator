package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.threading.ThreadPooling;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

/** Unfreeze lands on the room's next tick, as Habbo's does. */
class WiredEffectUnfreezeTickTest {

    @Test
    void theUserStaysFrozenUntilTheNextRoomTick() {
        Room room = mock(Room.class);
        long now = System.currentTimeMillis();
        when(room.getCycleTimestamp()).thenReturn(now - 200);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        WiredFreezeUtil.freeze(room, unit, 218, false);
        ThreadPooling threading = mock(ThreadPooling.class);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            platform.when(WiredPlatform::threading).thenReturn(threading);
            WiredEffectUnfreeze.unfreezeOnTheRoomTick(room, unit);
        }

        verify(threading).run(task.capture(), delay.capture());
        assertTrue(WiredFreezeUtil.isFrozen(unit));
        assertTrue(delay.getValue() > 0 && delay.getValue() <= 500, "delay " + delay.getValue());

        task.getValue().run();
        assertFalse(WiredFreezeUtil.isFrozen(unit));
    }

    @Test
    void withoutAThreadPoolTheUserIsUnfrozenAtOnce() {
        Room room = mock(Room.class);
        RoomUnit unit = new RoomUnit();
        unit.setRoom(room);
        WiredFreezeUtil.freeze(room, unit, 218, false);

        try (MockedStatic<WiredPlatform> platform = mockStatic(WiredPlatform.class)) {
            WiredEffectUnfreeze.unfreezeOnTheRoomTick(room, unit);
        }

        assertFalse(WiredFreezeUtil.isFrozen(unit));
    }
}
