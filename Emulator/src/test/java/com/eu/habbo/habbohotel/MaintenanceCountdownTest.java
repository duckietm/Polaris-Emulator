package com.eu.habbo.habbohotel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MaintenanceCountdownTest {
    private static final Path SOURCE = Path.of("src/main/java/com/eu/habbo/habbohotel/MaintenanceCountdown.java");

    @Test
    void minutesLeftRoundUpSoTheLastMinuteStillReadsOne() {
        assertEquals(5, MaintenanceCountdown.minutesLeft(1_000 + 300, 1_000));
        assertEquals(5, MaintenanceCountdown.minutesLeft(1_000 + 241, 1_000));
        assertEquals(1, MaintenanceCountdown.minutesLeft(1_000 + 1, 1_000));
        assertEquals(0, MaintenanceCountdown.minutesLeft(1_000, 1_000));
        assertEquals(0, MaintenanceCountdown.minutesLeft(900, 1_000));
    }

    @Test
    void remindersFallOnTheListedMinutesOnly() {
        for (int minutes : new int[] {30, 15, 10, 5, 3, 2, 1}) {
            assertTrue(MaintenanceCountdown.isReminder(minutes), minutes + " should remind");
        }

        assertFalse(MaintenanceCountdown.isReminder(4));
        assertFalse(MaintenanceCountdown.isReminder(0));
    }

    @Test
    void noCountdownRunsUntilOneIsStarted() {
        assertEquals(0, MaintenanceCountdown.getEndsAt());
        assertFalse(MaintenanceCountdown.cancel());
    }

    @Test
    void theEndSwitchesMaintenanceOnAndDisconnectsWithTheMaintenanceReason() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("MaintenanceMode.setEnabled(true, closingMessage)"));
        assertTrue(source.contains("MaintenanceMode.canLogin("), "staff at or above the maintenance rank stay online");
        assertTrue(source.contains("DisconnectReasonComposer.MAINTENANCE"));
        assertTrue(
                source.contains("tickGeneration != generation"),
                "a tick of a cancelled or replaced countdown must do nothing");
    }
}
