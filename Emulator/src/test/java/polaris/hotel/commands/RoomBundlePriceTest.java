package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A room bundle is never sold at a negative price or for a currency the hotel does not list. */
class RoomBundlePriceTest {

    private static final String TYPES = "0;5;101";

    @Test
    void aPriceInAListedCurrencyIsAccepted() {
        assertTrue(RoomBundleCommand.isValidPrice(100, 0, 0, TYPES));
        assertTrue(RoomBundleCommand.isValidPrice(0, 25, 5, TYPES));
        assertTrue(RoomBundleCommand.isValidPrice(0, 0, 999, TYPES), "no points, so the type does not matter");
    }

    @Test
    void negativePricesAndUnlistedCurrenciesAreRefused() {
        assertFalse(RoomBundleCommand.isValidPrice(-1, 0, 0, TYPES));
        assertFalse(RoomBundleCommand.isValidPrice(0, -5, 5, TYPES));
        assertFalse(RoomBundleCommand.isValidPrice(0, 10, 42, TYPES));
        assertFalse(RoomBundleCommand.isValidPrice(0, 10, 1, TYPES), "1 is not 101");
        assertFalse(RoomBundleCommand.isValidPrice(0, 10, 0, null));
    }
}
