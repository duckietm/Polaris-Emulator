package com.eu.habbo.habbohotel.rooms.raidprotection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RaidDetectorTest {
    private static final long QUIET = 120_000L;
    private static final int HIGH = RaidProtectionSettings.SENSITIVITY_HIGH;
    private static final int LOW = RaidProtectionSettings.SENSITIVITY_LOW;

    @Test
    void detectsARaidWhenEnoughDistinctUnknownVisitorsEnterWithinTheWindow() {
        RaidDetector detector = new RaidDetector();
        int threshold = RaidDetector.ENTRY_THRESHOLDS[HIGH];

        for (int user = 1; user < threshold; user++) {
            assertEquals(
                    RaidDetector.Kind.NONE,
                    detector.onEntry(user, HIGH, 1_000L * user, QUIET).kind());
        }
        RaidDetector.Result result = detector.onEntry(threshold, HIGH, 1_000L * threshold, QUIET);

        assertEquals(RaidDetector.Kind.STARTED, result.kind());
        assertEquals("entry", result.trigger());
        assertEquals(threshold, result.targets().size());
        assertTrue(detector.isIncidentActive(1_000L * threshold, QUIET));
        assertTrue(detector.isParticipant(1));
    }

    @Test
    void theSameVisitorReenteringIsNotARaid() {
        RaidDetector detector = new RaidDetector();
        for (int i = 0; i < 50; i++) {
            assertEquals(
                    RaidDetector.Kind.NONE,
                    detector.onEntry(7, HIGH, i * 100L, QUIET).kind());
        }
    }

    @Test
    void entriesOutsideTheWindowAreForgotten() {
        RaidDetector detector = new RaidDetector();
        int threshold = RaidDetector.ENTRY_THRESHOLDS[HIGH];
        long spacing = RaidDetector.ENTRY_WINDOW_MS / (threshold - 1) + 1_000L;

        for (int user = 1; user <= threshold * 3; user++) {
            assertEquals(
                    RaidDetector.Kind.NONE,
                    detector.onEntry(user, HIGH, spacing * user, QUIET).kind());
        }
    }

    @Test
    void lowSensitivityNeedsMoreVisitorsThanHigh() {
        assertTrue(RaidDetector.ENTRY_THRESHOLDS[LOW] > RaidDetector.ENTRY_THRESHOLDS[HIGH]);
        assertTrue(RaidDetector.CHAT_THRESHOLDS[LOW] > RaidDetector.CHAT_THRESHOLDS[HIGH]);

        RaidDetector detector = new RaidDetector();
        for (int user = 1; user <= RaidDetector.ENTRY_THRESHOLDS[HIGH]; user++) {
            assertEquals(
                    RaidDetector.Kind.NONE,
                    detector.onEntry(user, LOW, user, QUIET).kind());
        }
    }

    @Test
    void aChatFloodOfNewcomersIsARaidButChatOfOthersIsIgnored() {
        RaidDetector detector = new RaidDetector();
        detector.onEntry(1, HIGH, 0L, QUIET);
        detector.onEntry(2, HIGH, 0L, QUIET);
        detector.onEntry(3, HIGH, 0L, QUIET);

        assertEquals(
                RaidDetector.Kind.NONE, detector.onChat(99, HIGH, 10L, QUIET).kind());

        RaidDetector.Result result = RaidDetector.Result.NONE;
        int lines = RaidDetector.CHAT_THRESHOLDS[HIGH];
        for (int line = 0; line < lines; line++) {
            result = detector.onChat(1 + line % 3, HIGH, 100L + line, QUIET);
            if (line < lines - 1) assertEquals(RaidDetector.Kind.NONE, result.kind());
        }

        assertEquals(RaidDetector.Kind.STARTED, result.kind());
        assertEquals("chat", result.trigger());
        assertEquals(List.of(1, 2, 3), result.targets());
    }

    @Test
    void oneNewcomerSpammingIsNotARaid() {
        RaidDetector detector = new RaidDetector();
        detector.onEntry(1, HIGH, 0L, QUIET);
        for (int line = 0; line < 100; line++) {
            assertEquals(
                    RaidDetector.Kind.NONE,
                    detector.onChat(1, HIGH, line, QUIET).kind());
        }
    }

    @Test
    void duringAnIncidentEachSuspiciousEntryOrLineIsActedOnAndKeepsItGoing() {
        RaidDetector detector = startedIncident();
        long now = 30_000L;

        RaidDetector.Result entry = detector.onEntry(500, HIGH, now, QUIET);
        assertEquals(RaidDetector.Kind.CONTINUING, entry.kind());
        assertEquals(List.of(500), entry.targets());

        RaidDetector.Result chat = detector.onChat(500, HIGH, now + 1, QUIET);
        assertEquals(RaidDetector.Kind.CONTINUING, chat.kind());

        assertTrue(detector.isIncidentActive(now + QUIET - 1, QUIET));
        assertNull(detector.closeIfQuiet(now + QUIET - 1, QUIET));
    }

    @Test
    void anIncidentEndsAfterTheQuietTimeWithItsCounts() {
        RaidDetector detector = startedIncident();
        long lastActivity = 6_000L;
        detector.recordAction();
        detector.recordAction();
        detector.recordBlocked();

        assertFalse(detector.isIncidentActive(lastActivity + QUIET, QUIET));
        RaidDetector.Summary summary = detector.closeIfQuiet(lastActivity + QUIET + 5_000L, QUIET);

        assertNotNull(summary);
        assertEquals(lastActivity + QUIET, summary.endedMs());
        assertEquals(RaidDetector.ENTRY_THRESHOLDS[HIGH], summary.participants());
        assertEquals(2, summary.actioned());
        assertEquals(1, summary.blocked());
        assertNull(detector.closeIfQuiet(lastActivity + QUIET * 3, QUIET));
        assertFalse(detector.isParticipant(1));
    }

    @Test
    void theGuardKeepsAVisitorOutUntilItsTime() {
        RaidDetector detector = new RaidDetector();
        detector.block(5, 1_000L, 10_000L);

        assertTrue(detector.isBlocked(5, 9_999L));
        assertFalse(detector.isBlocked(5, 10_000L));
        assertFalse(detector.isBlocked(6, 5_000L));
    }

    @Test
    void stateStaysBounded() {
        RaidDetector detector = new RaidDetector();
        for (int user = 0; user < 10_000; user++) {
            detector.onEntry(user, LOW, user * 10_000L, QUIET);
            detector.block(user, user, user + 1);
        }
        // Old entries are pruned on the way; a burst afterwards still detects.
        long now = 10_000L * 10_000L + 1_000_000L;
        RaidDetector.Result result = RaidDetector.Result.NONE;
        for (int user = 0; user < RaidDetector.ENTRY_THRESHOLDS[LOW]; user++) {
            result = detector.onEntry(100_000 + user, LOW, now + user, QUIET);
        }
        assertEquals(RaidDetector.Kind.STARTED, result.kind());
    }

    private static RaidDetector startedIncident() {
        RaidDetector detector = new RaidDetector();
        RaidDetector.Result result = RaidDetector.Result.NONE;
        for (int user = 1; user <= RaidDetector.ENTRY_THRESHOLDS[HIGH]; user++) {
            result = detector.onEntry(user, HIGH, 1_000L * user, QUIET);
        }
        assertEquals(RaidDetector.Kind.STARTED, result.kind());
        return detector;
    }
}
