package polaris.hotel.items.interactions.wired.chest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;

/**
 * A rented furni is on loan: it expires back to the hotel, so handing it to someone
 * else through a chest sells something the renter does not own.
 */
class WiredChestRentedFurniTest {

    private static UserItem furni(boolean tradeable, boolean rented) {
        Item base = mock(Item.class);
        when(base.allowTrade()).thenReturn(tradeable);
        UserItem item = mock(UserItem.class);
        when(item.getBaseItem()).thenReturn(base);
        when(item.hasRentPeriod()).thenReturn(rented);
        return item;
    }

    @Test
    void anOwnedTradeableFurniGoesIn() {
        assertTrue(InteractionWiredChest.canStore(furni(true, false)));
    }

    @Test
    void aRentedFurniStaysOut() {
        assertFalse(InteractionWiredChest.canStore(furni(true, true)));
    }

    @Test
    void anUntradeableFurniStaysOutAsBefore() {
        assertFalse(InteractionWiredChest.canStore(furni(false, false)));
    }

    @Test
    void nothingIsNotStorable() {
        assertFalse(InteractionWiredChest.canStore(null));
    }
}
