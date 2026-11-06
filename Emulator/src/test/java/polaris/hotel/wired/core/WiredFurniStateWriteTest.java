package polaris.hotel.wired.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionWiredEffect;
import polaris.hotel.users.UserItem;

/** A variable write to @state (observed on AIR WIN63-202610201032). */
class WiredFurniStateWriteTest {

    @Test
    void aValueOutsideTheStatesSetsStateZero() {
        UserItem tile = furni(7, "1");
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

    private static UserItem furni(int states, String state) {
        UserItem item = mock(UserItem.class);
        Item base = mock(Item.class);
        when(base.getStateCount()).thenReturn(states);
        when(item.getBaseItem()).thenReturn(base);
        when(item.getExtradata()).thenReturn(state);
        return item;
    }
}
