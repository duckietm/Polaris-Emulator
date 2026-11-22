package polaris.hotel.items.interactions.wired.triggers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A repeater restarts on the first room tick after Reset Timers, then every interval (observed on AIR). */
class WiredRepeaterPhaseTest {

    @Test
    void untilAResetItKeepsTheSharedPhase() {
        WiredRepeaterPhase phase = new WiredRepeaterPhase();
        assertTrue(phase.shouldFire(true, 1_000L, 1_020L, 500L));
        assertFalse(phase.shouldFire(false, 1_000L, 1_070L, 500L));
    }

    @Test
    void afterAResetItFiresOnTheNextRoomTickAndThenEveryInterval() {
        WiredRepeaterPhase phase = new WiredRepeaterPhase();
        // Room ticks at 2 050, 2 550, ...; reset at 2 150.
        phase.reset(2_150L);

        List<Long> fired = new ArrayList<>();
        for (long now = 2_150L; now <= 3_400L; now += 50L) {
            if (phase.shouldFire(now % 200L == 0, 2_050L, now, 200L)) fired.add(now);
        }

        assertEquals(List.of(2_550L, 2_750L, 2_950L, 3_150L, 3_350L), fired);
    }

    @Test
    void aTickAWholePeriodLateSkipsTheMissedPeriods() {
        WiredRepeaterPhase phase = new WiredRepeaterPhase();
        phase.reset(0L);
        assertTrue(phase.shouldFire(false, 0L, 500L, 500L));
        assertTrue(phase.shouldFire(false, 0L, 2_100L, 500L));
        assertFalse(phase.shouldFire(false, 0L, 2_400L, 500L));
        assertTrue(phase.shouldFire(false, 0L, 2_500L, 500L));
    }

    @Test
    void aRepeaterPlacedAfterAResetJoinsTheRoomTimer() {
        // The room's timers were reset at 2 550; a repeater that was there fires at 2 550, 3 550, 4 550, ...
        WiredRepeaterPhase late = new WiredRepeaterPhase();
        late.join(2_550L, 3_900L, 1_000L);

        List<Long> fired = new ArrayList<>();
        for (long now = 3_900L; now <= 6_600L; now += 50L) {
            if (late.shouldFire(now % 1_000L == 0, 3_550L, now, 1_000L)) fired.add(now);
        }

        assertEquals(List.of(4_550L, 5_550L, 6_550L), fired);
    }

    @Test
    void joiningDoesNothingWithoutARoomResetOrAfterTheRepeatersOwnReset() {
        WiredRepeaterPhase untouched = new WiredRepeaterPhase();
        untouched.join(-1L, 3_900L, 1_000L);
        assertTrue(untouched.shouldFire(true, 3_550L, 3_900L, 1_000L));

        WiredRepeaterPhase reset = new WiredRepeaterPhase();
        reset.reset(5_000L);
        reset.join(2_550L, 5_010L, 1_000L);
        assertFalse(reset.shouldFire(true, 5_000L, 5_010L, 1_000L));
        assertTrue(reset.shouldFire(false, 5_000L, 5_500L, 1_000L));
    }
}
