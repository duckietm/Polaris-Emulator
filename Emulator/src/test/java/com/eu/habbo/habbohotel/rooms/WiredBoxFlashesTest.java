package com.eu.habbo.habbohotel.rooms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.users.HabboItem;
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
        WiredBoxFlashes flashes = new WiredBoxFlashes();
        HabboItem trigger = box("0");
        HabboItem effect = box("1");

        assertTrue(flashes.flip(trigger, WiredBoxFlashes::toggle));
        assertTrue(flashes.flip(effect, WiredBoxFlashes::toggle));
        assertFalse(flashes.flip(trigger, WiredBoxFlashes::toggle));

        assertEquals("1", trigger.getExtradata());
        assertEquals("0", effect.getExtradata());
        assertEquals(List.of(trigger, effect), List.copyOf(flashes.drain()));
        assertTrue(flashes.drain().isEmpty());

        // The next window flips it again.
        assertTrue(flashes.flip(trigger, WiredBoxFlashes::toggle));
        assertEquals("0", trigger.getExtradata());
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
