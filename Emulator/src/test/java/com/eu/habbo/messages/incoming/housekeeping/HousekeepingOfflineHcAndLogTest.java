package com.eu.habbo.messages.incoming.housekeeping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Adding HC days to an offline user started from now and threw away what was left; the action log, with the
 * text of every internal note in it, was open to any operator.
 */
class HousekeepingOfflineHcAndLogTest {
    private static final Path BASE = Path.of("src/main/java/com/eu/habbo/messages/incoming/housekeeping");

    private static String source(String name) throws Exception {
        return Files.readString(BASE.resolve(name)).replaceAll("\\s+", " ");
    }

    @Test
    void anOfflineSubscriptionIsExtendedFromItsStoredExpiry() throws Exception {
        String source = source("HousekeepingSetHcSubscriptionEvent.java");

        assertTrue(source.contains("SELECT club_expire_timestamp FROM users_settings WHERE user_id = ?"));
        assertTrue(source.contains("Math.max(storedClubExpire(connection, userId), now)"));
        assertFalse(source.contains("best-effort offline"));
    }

    @Test
    void theLogIsTheUsersAreasAndHoldsNoNoteText() throws Exception {
        assertTrue(source("HousekeepingListActionLogEvent.java").contains("return HousekeepingAreas.USERS;"));

        String note = source("HousekeepingUserNoteEvent.java");
        assertTrue(note.contains("\"note_length=\" + note.length()"));
        assertFalse(note.contains("\"note=\" +"));
    }
}
