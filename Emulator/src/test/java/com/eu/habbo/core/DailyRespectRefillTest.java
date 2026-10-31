package com.eu.habbo.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Every start refilled the daily respects of every user; the time of the last refill is now kept. */
class DailyRespectRefillTest {

    private static final int DAY = 86400;

    @Test
    void aRestartWithinTheCooldownDoesNotRefill() {
        assertFalse(CleanerThread.dailyRefillDue(1_000_000 + 600, 1_000_000, DAY));
    }

    @Test
    void theCooldownElapsedRefills() {
        assertTrue(CleanerThread.dailyRefillDue(1_000_000 + DAY + 1, 1_000_000, DAY));
    }

    @Test
    void aHotelThatNeverStoredARefillRefills() {
        assertTrue(CleanerThread.dailyRefillDue(1_000_000, 0, DAY));
    }

    @Test
    void theRefillIsStoredAndOneUserCannotStopTheOthers() throws Exception {
        String cleaner = Files.readString(Path.of("src/main/java/com/eu/habbo/core/CleanerThread.java"));

        assertTrue(cleaner.contains("statement.setString(1, LAST_DAILY_REFILL_KEY);"));
        assertTrue(cleaner.contains("LAST_DAILY_REFILL = Emulator.getConfig().getInt(LAST_DAILY_REFILL_KEY, 0);"));
        int loop = cleaner.indexOf("for (Habbo habbo : Emulator.getGameEnvironment()");
        int guard = cleaner.indexOf("try {", loop);
        assertTrue(loop > 0 && guard > loop && guard < cleaner.indexOf("new UserDataComposer(habbo)", loop));
    }
}
