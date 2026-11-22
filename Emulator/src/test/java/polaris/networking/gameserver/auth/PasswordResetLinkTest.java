package polaris.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The reset mail pointed at http://localhost/reset-password, and nothing took the token it carried: a hotel that
 * lets players log in through the client had no way to finish a reset.
 */
class PasswordResetLinkTest {

    private static final String TOKEN = "AbCdEfGhIjKlMnOpQrStUvWxYz012345_-abcdefghi";

    @Test
    void anUnsetAddressUsesTheClientTheRequestCameFrom() {
        assertEquals(
                "https://hotel.example/?reset_token=" + TOKEN,
                SessionEndpoints.resetLink("", "https://hotel.example", TOKEN));
        // The address a hotel had before it could be left unset counts as unset.
        assertEquals(
                "https://hotel.example/?reset_token=" + TOKEN,
                SessionEndpoints.resetLink("http://localhost/reset-password", "https://hotel.example", TOKEN));
    }

    @Test
    void withoutATrustedOriginItFallsBackToLocalhost() {
        assertTrue(SessionEndpoints.resetLink("", null, TOKEN).startsWith(SessionEndpoints.RESET_URL_FALLBACK));
        assertTrue(SessionEndpoints.resetLink(null, " ", TOKEN).startsWith(SessionEndpoints.RESET_URL_FALLBACK));
    }

    @Test
    void aConfiguredAddressWinsAndKeepsTheCmsForm() {
        assertEquals(
                "https://cms.example/reset-password?token=" + TOKEN,
                SessionEndpoints.resetLink("https://cms.example/reset-password", "https://hotel.example", TOKEN));
        assertEquals(
                "https://cms.example/reset?lang=nl&token=" + TOKEN,
                SessionEndpoints.resetLink("https://cms.example/reset?lang=nl", null, TOKEN));
    }

    @Test
    void aPlaceholderPutsTheTokenWhereTheHotelWantsIt() {
        assertEquals(
                "https://hotel.example/?reset_token=" + TOKEN,
                SessionEndpoints.resetLink("https://hotel.example/?reset_token=%token%", null, TOKEN));
    }

    @Test
    void onlyATokenOfTheMintedShapeIsLookedUp() {
        assertTrue(SessionEndpoints.isResetTokenShape(AuthHttpUtil.mintResetToken()));
        assertTrue(SessionEndpoints.isResetTokenShape(TOKEN));
        assertFalse(SessionEndpoints.isResetTokenShape(""));
        assertFalse(SessionEndpoints.isResetTokenShape(null));
        assertFalse(SessionEndpoints.isResetTokenShape("short"));
        assertFalse(SessionEndpoints.isResetTokenShape(TOKEN + "' OR 1=1"));
        assertFalse(SessionEndpoints.isResetTokenShape("a".repeat(129)));
    }

    @Test
    void aTokenIsGoodForItsHourOnly() {
        long now = 2_000_000_000L;

        assertFalse(SessionEndpoints.resetTokenExpired(Timestamp.from(Instant.ofEpochSecond(now + 1)), now));
        assertTrue(SessionEndpoints.resetTokenExpired(Timestamp.from(Instant.ofEpochSecond(now)), now));
        assertTrue(SessionEndpoints.resetTokenExpired(Timestamp.from(Instant.ofEpochSecond(now - 60)), now));
        assertTrue(SessionEndpoints.resetTokenExpired(null, now));
    }

    @Test
    void theResetIsLimitedUsesTheTokenOnceAndSignsEveryoneOut() throws Exception {
        String source =
                Files.readString(Path.of("src/main/java/polaris/networking/gameserver/auth/SessionEndpoints.java"));
        int start = source.indexOf("static void handleResetPassword(");
        String body = source.substring(start, source.indexOf("static boolean resetTokenExpired(", start));

        int probe = body.indexOf("AuthRateLimiter.tryProbe(ip)");
        int use = body.indexOf("DELETE FROM password_resets WHERE user_id = ? AND token = ?");
        int update = body.indexOf("UPDATE users SET password = ?");

        assertTrue(probe >= 0 && probe < body.indexOf("password_resets"), "rate limit before the first lookup");
        assertTrue(use >= 0 && use < update, "the token is used up before the password is written");
        assertTrue(body.contains("if (used != 1)"), "a token already used changes nothing");
        assertTrue(body.contains("access_token_version = access_token_version + 1"));
        assertTrue(body.contains("RememberJwtService.revokeAllForUser(conn, userId)"));

        String handler =
                Files.readString(Path.of("src/main/java/polaris/networking/gameserver/auth/AuthHttpHandler.java"));
        assertTrue(handler.contains("case RESET_PATH -> SessionEndpoints.handleResetPassword(ctx, req, body, ip);"));
    }

    @Test
    void aWildcardWhitelistIsNotTrustedForTheLink() throws Exception {
        String gate = Files.readString(Path.of("src/main/java/polaris/networking/gameserver/auth/CorsOriginGate.java"));
        int start = gate.indexOf("public static String linkableOrigin(");
        String body = gate.substring(start, gate.indexOf("static boolean matchesOrigin(", start));

        assertTrue(body.contains("if (!isAllowed(req)) return null;"));
        assertTrue(body.contains("if (\"*\".equals(entry.trim())) return null;"));
    }
}
