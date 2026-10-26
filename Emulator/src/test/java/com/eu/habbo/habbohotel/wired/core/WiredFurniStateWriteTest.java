package com.eu.habbo.habbohotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.users.HabboItem;
import org.junit.jupiter.api.Test;

/** A variable write to @state (observed on AIR WIN63-202610201032). */
class WiredFurniStateWriteTest {

    @Test
    void aValueOutsideTheStatesSetsStateZero() {
        HabboItem tile = furni(7, "1");
        for (int value : new int[] {7, 8, 24, 1000, Integer.MAX_VALUE, -1}) {
            assertEquals(0, WiredInternalVariableSupport.furniStateWrite(tile, value), "value " + value);
        }
        assertEquals(6, WiredInternalVariableSupport.furniStateWrite(tile, 6));
    }

    @Test
    void theStateItAlreadyHasIsNoWrite() {
        assertNull(WiredInternalVariableSupport.furniStateWrite(furni(2, "1"), 1));
        assertEquals(1, WiredInternalVariableSupport.furniStateWrite(furni(2, ""), 1));
    }

    @Test
    void aFurniWithoutStatesAndAWiredBoxAreLeftAlone() {
        assertNull(WiredInternalVariableSupport.furniStateWrite(furni(0, "0"), 1));
        InteractionWiredEffect box = mock(InteractionWiredEffect.class);
        Item base = mock(Item.class);
        when(base.getStateCount()).thenReturn(2);
        when(box.getBaseItem()).thenReturn(base);
        assertNull(WiredInternalVariableSupport.furniStateWrite(box, 1));
    }

    private static HabboItem furni(int states, String state) {
        HabboItem item = mock(HabboItem.class);
        Item base = mock(Item.class);
        when(base.getStateCount()).thenReturn(states);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn(state);
        return item;
    }
}
