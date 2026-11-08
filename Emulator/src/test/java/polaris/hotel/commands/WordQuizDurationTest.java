package polaris.hotel.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A word quiz runs between 5 seconds and an hour, whatever number the command was given. */
class WordQuizDurationTest {

    @Test
    void theDurationIsClampedBeforeItBecomesMilliseconds() {
        assertEquals(60, WordQuizCommand.clampDurationSeconds(60));
        assertEquals(5, WordQuizCommand.clampDurationSeconds(0));
        assertEquals(5, WordQuizCommand.clampDurationSeconds(-30));
        assertEquals(3600, WordQuizCommand.clampDurationSeconds(Integer.MAX_VALUE));
        assertEquals(3_600_000, WordQuizCommand.clampDurationSeconds(3_000_000) * 1000, "no overflow");
    }
}
