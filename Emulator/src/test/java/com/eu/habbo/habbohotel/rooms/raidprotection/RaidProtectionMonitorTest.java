package com.eu.habbo.habbohotel.rooms.raidprotection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RaidProtectionMonitorTest {
    private static final long NOW = 1_757_600_000L;

    @Test
    @DisplayName("a room filling up quietly never trips, even at the highest sensitivity")
    void quietArrivalsDoNotTrip() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        for (int userId = 1; userId <= 7; userId++) {
            monitor.recordArrival(userId, NOW);
        }

        assertNull(monitor.evaluate(RaidProtectionSettings.SENSITIVITY_HIGH, NOW));
    }

    @Test
    @DisplayName("arrivals that immediately start talking trip the highest sensitivity")
    void arrivalsThatTalkTrip() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        for (int userId = 1; userId <= 3; userId++) {
            monitor.recordArrival(userId, NOW);
            monitor.recordMessage(userId, "hello " + userId, NOW + 1);
        }

        assertEquals(9, monitor.currentScore(NOW + 1));

        Set<Integer> contributors = monitor.evaluate(RaidProtectionSettings.SENSITIVITY_HIGH, NOW + 1);

        assertNotNull(contributors);
        assertEquals(Set.of(1, 2, 3), contributors);
    }

    @Test
    @DisplayName("the same text from a second user scores extra")
    void echoedTextScoresExtra() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        monitor.recordArrival(1, NOW);
        monitor.recordArrival(2, NOW);
        monitor.recordMessage(1, "JOIN OUR ROOM", NOW + 1);
        monitor.recordMessage(2, "join our room", NOW + 2);

        // Two arrivals, one newcomer message, one echoed newcomer message.
        assertEquals(2 + 2 + 5, monitor.currentScore(NOW + 2));
    }

    @Test
    @DisplayName("a message from someone who was already in the room does not score")
    void residentMessagesDoNotScore() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        monitor.recordMessage(99, "anyone here?", NOW);

        assertEquals(0, monitor.currentScore(NOW));
    }

    @Test
    @DisplayName("a newcomer stops being one once the arrival window has passed")
    void newcomerWindowExpires() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();
        long later = NOW + RaidProtectionMonitor.RECENT_ARRIVAL_SECONDS + 1;

        monitor.recordArrival(1, NOW);
        monitor.recordMessage(1, "still here", later);

        assertEquals(0, monitor.currentScore(later));
    }

    @Test
    @DisplayName("events older than the window stop counting")
    void windowSlides() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        for (int userId = 1; userId <= 8; userId++) {
            monitor.recordArrival(userId, NOW);
        }

        assertEquals(8, monitor.currentScore(NOW));
        assertEquals(0, monitor.currentScore(NOW + RaidProtectionMonitor.WINDOW_SECONDS + 1));
    }

    @Test
    @DisplayName("the three sensitivities are three thresholds, the highest being the easiest to trip")
    void thresholdsAreOrdered() {
        int low = RaidProtectionMonitor.thresholdFor(RaidProtectionSettings.SENSITIVITY_LOW);
        int medium = RaidProtectionMonitor.thresholdFor(RaidProtectionSettings.SENSITIVITY_MEDIUM);
        int high = RaidProtectionMonitor.thresholdFor(RaidProtectionSettings.SENSITIVITY_HIGH);

        assertTrue(low > medium);
        assertTrue(medium > high);
    }

    @Test
    @DisplayName("one burst produces one incident, not an incident per message after it")
    void firingClearsTheWindow() {
        RaidProtectionMonitor monitor = new RaidProtectionMonitor();

        for (int userId = 1; userId <= 3; userId++) {
            monitor.recordArrival(userId, NOW);
            monitor.recordMessage(userId, "raid", NOW + 1);
        }

        assertNotNull(monitor.evaluate(RaidProtectionSettings.SENSITIVITY_HIGH, NOW + 1));
        assertNull(monitor.evaluate(RaidProtectionSettings.SENSITIVITY_HIGH, NOW + 1));
    }

    @Test
    @DisplayName("settings outside the values the client offers are refused")
    void invalidSettingsAreRefused() {
        assertTrue(RaidProtectionSettings.defaults(7).isValid());

        RaidProtectionSettings badDuration = new RaidProtectionSettings(
                7,
                true,
                RaidProtectionSettings.SENSITIVITY_HIGH,
                RaidProtectionSettings.ACTION_TEMPORARY_BAN,
                42,
                false,
                1800,
                RaidProtectionSettings.SENSITIVITY_HIGH);
        RaidProtectionSettings badSensitivity = new RaidProtectionSettings(
                7,
                true,
                9,
                RaidProtectionSettings.ACTION_KICK,
                3600,
                false,
                1800,
                RaidProtectionSettings.SENSITIVITY_HIGH);

        assertFalse(badDuration.isValid());
        assertFalse(badSensitivity.isValid());
    }
}
