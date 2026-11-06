package polaris.hotel.items.interactions.wired.conditions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WiredDateRangeInputGuardTest {

    @Test
    void timestampsAreNonNegative() {
        assertEquals(0, WiredDateRangeInputGuard.normalizeTimestamp(-1));
        assertEquals(42, WiredDateRangeInputGuard.normalizeTimestamp(42));
    }

    @Test
    void validRangesArePreserved() {
        assertArrayEquals(new int[] {100, 200}, WiredDateRangeInputGuard.normalizeRange(100, 200));
    }

    @Test
    void negativeAndInvertedRangesBecomeInactive() {
        assertArrayEquals(new int[] {0, 0}, WiredDateRangeInputGuard.normalizeRange(-10, -1));
        assertArrayEquals(new int[] {0, 0}, WiredDateRangeInputGuard.normalizeRange(200, 100));
    }

    @org.junit.jupiter.api.Test
    void anEndOfZeroKeepsTheRangeOpen() {
        assertArrayEquals(new int[] {100, 0}, WiredDateRangeInputGuard.normalizeRange(100, 0));
    }
}
