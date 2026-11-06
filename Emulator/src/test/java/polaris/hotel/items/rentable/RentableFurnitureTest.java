package polaris.hotel.items.rentable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RentableFurnitureTest {
    private static final int NOW = 1_800_000_000;
    private static final int DAY = RentableFurniture.SECONDS_PER_DAY;

    @Test
    void furniOwnedOutrightHasNoRentPeriodAndSerialisesMinusOne() {
        assertFalse(RentableFurniture.hasRentPeriod(RentableFurniture.NEVER));
        assertFalse(RentableFurniture.hasRentPeriod(0));
        assertEquals(-1, RentableFurniture.secondsToExpiration(RentableFurniture.NEVER, NOW));
        assertEquals(-1, RentableFurniture.secondsToExpiration(0, NOW));
    }

    @Test
    void secondsLeftCountDownToZeroAndNeverGoNegative() {
        assertTrue(RentableFurniture.hasRentPeriod(NOW + 90));
        assertEquals(90, RentableFurniture.secondsToExpiration(NOW + 90, NOW));
        assertEquals(0, RentableFurniture.secondsToExpiration(NOW - 5, NOW));
    }

    @Test
    void aFreshRentalLastsTheOfferDays() {
        assertEquals(NOW + 7 * DAY, RentableFurniture.expiresAfter(NOW, 7));
        assertEquals(NOW, RentableFurniture.expiresAfter(NOW, -3));
    }

    @Test
    void extendingAddsTheDaysToWhatIsLeft() {
        int expires = NOW + 2 * DAY;
        assertEquals(NOW + 9 * DAY, RentableFurniture.extend(expires, NOW, 7));
    }

    @Test
    void extendingAnExpiredRentalRestartsFromNow() {
        assertEquals(NOW + 7 * DAY, RentableFurniture.extend(NOW - DAY, NOW, 7));
    }
}
