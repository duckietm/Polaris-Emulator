package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * /auth/forgot-password had no limit: it mailed any address as often as asked (an inbox could be flooded
 * from one script) and replaced the account's reset token every time.
 */
class ForgotPasswordLimitTest {

    private static Timestamp sentAt(long epochSecond) {
        return Timestamp.from(Instant.ofEpochSecond(epochSecond + SessionEndpoints.RESET_TOKEN_LIFETIME_SECONDS));
    }

    @Test
    void anotherMailWaitsTwoMinutes() {
        long now = 2_000_000_000L;

        assertTrue(SessionEndpoints.resetSentRecently(sentAt(now - 30), now));
        assertTrue(SessionEndpoints.resetSentRecently(sentAt(now - SessionEndpoints.RESET_RESEND_SECONDS + 1), now));
        assertFalse(SessionEndpoints.resetSentRecently(sentAt(now - SessionEndpoints.RESET_RESEND_SECONDS), now));
        assertFalse(SessionEndpoints.resetSentRecently(null, now), "no earlier mail, nothing to wait for");
    }

    @Test
    void theEndpointIsRateLimitedAndChecksTheLastMailBeforeANewToken() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));
        int forgot = source.indexOf("static void handleForgot(");
        String body = source.substring(forgot, source.indexOf("private static String bearerToken(", forgot));

        int probe = body.indexOf("AuthRateLimiter.tryProbe(ip)");
        int recent = body.indexOf("resetSentRecently(previous.getTimestamp(\"expires_at\"), now)");
        int mint = body.indexOf("mintResetToken()");

        assertTrue(probe > 0 && probe < recent && recent < mint, body);
    }
}
