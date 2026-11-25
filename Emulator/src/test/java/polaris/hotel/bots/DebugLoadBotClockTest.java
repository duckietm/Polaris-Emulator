package polaris.hotel.bots;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DebugLoadBotClockTest {
    @Test
    void theFirstLineComesAnywhereInThePeriod() {
        assertEquals(1L, DebugLoadBot.nextPause(5000, 0d));
        assertEquals(2500L, DebugLoadBot.nextPause(5000, 0.5d));
        assertEquals(5000L, DebugLoadBot.nextPause(5000, 1d));
    }

    @Test
    void aFractionOutOfRangeStaysInsideThePeriod() {
        assertEquals(1L, DebugLoadBot.nextPause(5000, -3d));
        assertEquals(5000L, DebugLoadBot.nextPause(5000, 7d));
    }
}
