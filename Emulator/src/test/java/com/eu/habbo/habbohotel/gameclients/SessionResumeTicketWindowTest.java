package com.eu.habbo.habbohotel.gameclients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * A disconnect puts the used SSO ticket back so the client can reconnect. It used to stay there with
 * no end, so a ticket seen once in a log or a URL opened the account at any later time.
 */
class SessionResumeTicketWindowTest {

    @Test
    void theWindowIsNeverShorterThanTheGracePeriod() {
        assertEquals(120, SessionResumeManager.restoredTicketSeconds(30, 120));
        assertEquals(300, SessionResumeManager.restoredTicketSeconds(300, 120));
        assertEquals(1, SessionResumeManager.restoredTicketSeconds(0, 0));
    }

    @Test
    void theRestoredTicketIsClearedOnlyWhileItIsStillTheSameTicket() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/habbohotel/gameclients/SessionResumeManager.java"));

        assertTrue(source.contains("expireRestoredTicket(userId, ssoTicket)"), "a restore schedules its end");
        assertTrue(
                source.contains("UPDATE users SET auth_ticket = '' WHERE id = ? AND auth_ticket = ?"),
                "a newer ticket written by the CMS is never cleared");
    }
}
