package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WiredBoxFlashesTest {

    private static HabboItem box(String state) {
        AtomicReference<String> data = new AtomicReference<>(state);
        HabboItem item = mock(HabboItem.class);

        when(item.getExtradata()).thenAnswer(invocation -> data.get());
        doAnswer(invocation -> {
                    data.set(invocation.getArgument(0));
                    return null;
                })
                .when(item)
                .setExtradata(anyString());

        return item;
    }

    @Test
    void aBoxFlipsOncePerWindowAndTheCycleTakesThemInOrder() {
        long[] now = {10_000L};
        WiredBoxFlashes flashes = new WiredBoxFlashes(null, () -> now[0]);
        HabboItem trigger = box("0");
        HabboItem effect = box("1");

        assertTrue(flashes.flip(trigger, WiredBoxFlashes::toggle));
        assertTrue(flashes.flip(effect, WiredBoxFlashes::toggle));
        assertFalse(flashes.flip(trigger, WiredBoxFlashes::toggle));

        assertEquals("1", trigger.getExtradata());
        assertEquals("0", effect.getExtradata());
        assertEquals(List.of(trigger, effect), List.copyOf(flashes.drain()));
        assertTrue(flashes.drain().isEmpty());

        // Still inside the box's own 500 ms window: no flip, even after a drain.
        now[0] += 499;
        assertFalse(flashes.flip(trigger, WiredBoxFlashes::toggle));

        // The next window flips it again.
        now[0] += 1;
        assertTrue(flashes.flip(trigger, WiredBoxFlashes::toggle));
        assertEquals("0", trigger.getExtradata());
    }

    @Test
    void twoFiringsTwoHundredMillisecondsApartFlashTwoHundredMillisecondsApart() {
        long[] now = {10_000L};
        WiredBoxFlashes flashes = new WiredBoxFlashes(null, () -> now[0]);
        HabboItem first = box("0");
        HabboItem second = box("0");

        assertTrue(flashes.flip(first, WiredBoxFlashes::toggle));
        now[0] += 200;
        assertTrue(flashes.flip(second, WiredBoxFlashes::toggle));
    }

    @Test
    void aDispatchSendsItsFlashesWhenItEndsNotOnTheNextCycle() {
        Room room = mock(Room.class);
        WiredBoxFlashes flashes = new WiredBoxFlashes(room, () -> 10_000L);
        HabboItem trigger = box("0");

        WiredBoxFlashes.openPass();
        try {
            flashes.flip(trigger, WiredBoxFlashes::toggle);
            verify(room, never()).sendComposer(any(ServerMessage.class));
        } finally {
            WiredBoxFlashes.closePass();
        }

        verify(room, times(1)).sendComposer(any(ServerMessage.class));
        assertFalse(flashes.isPending(trigger));
    }

    @Test
    void aFiringSendsItsFurniStatesAndFlashesInOnePacketAheadOfTheMovements() {
        Room room = mock(Room.class);
        WiredBoxFlashes flashes = new WiredBoxFlashes(room, () -> 10_000L);
        HabboItem trigger = box("0");
        HabboItem furni = box("0");
        Item base = mock(Item.class);
        when(base.getType()).thenReturn(FurnitureType.FLOOR);
        when(furni.getBaseItem()).thenReturn(base);

        WiredBoxFlashes.openPass();
        try {
            flashes.flip(trigger, WiredBoxFlashes::toggle);
            WiredFurniStateBatch.open();
            assertTrue(WiredFurniStateBatch.defer(room, furni));
            WiredFurniStateBatch.close();
            // The batch closed (where the movements follow): the states and the flash went out together.
            verify(room, times(1)).sendComposer(any(ServerMessage.class));
        } finally {
            WiredBoxFlashes.closePass();
        }

        verify(room, times(1)).sendComposer(any(ServerMessage.class));
        assertFalse(flashes.isPending(trigger));
    }

    @Test
    void aRoomThatHidesItsWiredKeepsTheFlashesForTheCycle() {
        Room room = mock(Room.class);
        when(room.isHideWired()).thenReturn(true);
        WiredBoxFlashes flashes = new WiredBoxFlashes(room, () -> 10_000L);
        HabboItem trigger = box("0");

        WiredBoxFlashes.openPass();
        flashes.flip(trigger, WiredBoxFlashes::toggle);
        WiredBoxFlashes.closePass();

        verify(room, never()).sendComposer(any(ServerMessage.class));
        assertTrue(flashes.isPending(trigger));
    }

    @Test
    void selectorsAlternateAboveTheirRestingState() {
        assertEquals("1", WiredBoxFlashes.selector("0", false));
        assertEquals("2", WiredBoxFlashes.selector("1", false));
        assertEquals("1", WiredBoxFlashes.selector("2", false));

        assertEquals("4", WiredBoxFlashes.selector("3", true));
        assertEquals("5", WiredBoxFlashes.selector("4", true));
        assertEquals("4", WiredBoxFlashes.selector("5", true));
        // A selector switched to filter mode starts from its resting state.
        assertEquals("4", WiredBoxFlashes.selector("1", true));
    }
}
