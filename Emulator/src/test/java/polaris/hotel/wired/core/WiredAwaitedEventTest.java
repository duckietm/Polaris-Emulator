package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.api.IWiredTrigger;
import polaris.hotel.wired.api.WiredStack;

/** Clock ticks and bot steps reach the engine only when a stack's trigger waits for them. */
class WiredAwaitedEventTest {

    private final WiredEngine engine = mock(WiredEngine.class);
    private final RoomWiredStackIndex stackIndex = mock(RoomWiredStackIndex.class);
    private final Room room = mock(Room.class);

    @BeforeEach
    void installManager() throws ReflectiveOperationException {
        setStaticField("engine", this.engine);
        setStaticField("stackIndex", this.stackIndex);
        setStaticField("initialized", true);
        when(this.engine.handleEvent(any(WiredEvent.class), anyBoolean())).thenReturn(true);
    }

    @AfterEach
    void resetManager() throws ReflectiveOperationException {
        setStaticField("initialized", false);
        setStaticField("engine", null);
        setStaticField("stackIndex", null);
    }

    @Test
    void aClockTickReachesTheStacksWaitingForThatCounter() {
        UserItem counter = mock(UserItem.class);
        givenStack(WiredEvent.Type.CLOCK_COUNTER_REACHED, true);

        assertTrue(WiredManager.triggerClockCounter(this.room, counter));
        verify(this.engine).handleEvent(any(WiredEvent.class), eq(false));
        verify(this.engine, never()).handleEventForSourceItem(any(), anyInt());
    }

    @Test
    void aClockTickNoStackWaitsForIsDroppedBeforeTheEngine() {
        UserItem counter = mock(UserItem.class);
        givenStack(WiredEvent.Type.CLOCK_COUNTER_REACHED, false);

        assertFalse(WiredManager.triggerClockCounter(this.room, counter));
        verify(this.engine, never()).handleEvent(any(WiredEvent.class), anyBoolean());
    }

    @Test
    void aBotReachingFurniFiresOnlyForAWaitingStack() {
        UserItem furni = mock(UserItem.class);
        RoomUnit bot = mock(RoomUnit.class);
        when(this.room.getLayout()).thenReturn(mock(RoomLayout.class));
        givenStack(WiredEvent.Type.BOT_REACHED_FURNI, false);

        assertFalse(WiredManager.triggerBotReachedFurni(this.room, bot, furni));
        verify(this.engine, never()).handleEvent(any(WiredEvent.class), anyBoolean());

        givenStack(WiredEvent.Type.BOT_REACHED_FURNI, true);

        assertTrue(WiredManager.triggerBotReachedFurni(this.room, bot, furni));
    }

    private void givenStack(WiredEvent.Type type, boolean matches) {
        IWiredTrigger trigger = mock(IWiredTrigger.class);
        UserItem triggerItem = mock(UserItem.class);
        when(trigger.matches(any(), any())).thenReturn(matches);
        WiredStack stack = mock(WiredStack.class);
        when(stack.trigger()).thenReturn(trigger);
        when(stack.triggerItem()).thenReturn(triggerItem);
        when(this.stackIndex.getStacks(this.room, type)).thenReturn(List.of(stack));
    }

    private static void setStaticField(String name, Object value) throws ReflectiveOperationException {
        Field field = WiredManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
}
