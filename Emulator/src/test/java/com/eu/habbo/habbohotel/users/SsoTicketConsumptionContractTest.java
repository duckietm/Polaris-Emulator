package com.eu.habbo.habbohotel.users;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * SSO tickets are single-use: the login path consumes (clears) the ticket
 * after a successful login unless the debug_sso emulator setting is enabled,
 * and no longer gates the game login on auth_ticket_expires_at (consumption
 * is the replay bound; third-party CMSes only write auth_ticket). The
 * session-resume parking flow depends on the cleared state: its restore
 * guard only re-writes the parked ticket while the column is empty, and
 * newer clients additionally carry a session-recovery token.
 */
class SsoTicketConsumptionContractTest {

    private static final Path HABBO_MANAGER = Path.of("src/main/java/com/eu/habbo/habbohotel/users/HabboManager.java");
    private static final Path SECURE_LOGIN =
            Path.of("src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java");
    private static final Path SESSION_ENDPOINTS =
            Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

    @Test
    void loadHabboConsumesTheTicketBehindTheDebugSetting() throws Exception {
        String source = Files.readString(HABBO_MANAGER);

        assertTrue(
                source.contains(
                        "UPDATE users SET auth_ticket = '', auth_ticket_expires_at = NULL WHERE id = ? LIMIT 1"),
                "a successful SSO login must consume the single-use ticket");
        assertTrue(
                source.contains("getBoolean(\"debug_sso\", false)"),
                "debug_sso must keep tickets alive for debugging and default to off");

        int consume = source.indexOf("public void consumeSsoTicket(");
        assertTrue(consume >= 0, "the consumption helper must stay public for the resume path");
        int load = source.indexOf("public Habbo loadHabbo(String sso)");
        assertTrue(
                load >= 0 && source.indexOf("consumeSsoTicket(", load) > load,
                "the SSO login path must consume the ticket after building the habbo");
    }

    @Test
    void gameLoginNoLongerGatesOnTicketExpiry() throws Exception {
        String habboManager = Files.readString(HABBO_MANAGER);
        String secureLogin = Files.readString(SECURE_LOGIN);

        assertFalse(
                habboManager.contains("auth_ticket_expires_at IS NULL OR auth_ticket_expires_at >= NOW()"),
                "single-use tickets are bounded by consumption, not expiry; the expiry gate blocked CMSes that only write auth_ticket");
        assertFalse(
                secureLogin.contains("auth_ticket_expires_at >="),
                "the resume lookup must not gate on ticket expiry either");
    }

    @Test
    void sessionResumeReconsumesTheRestoredTicket() throws Exception {
        String source = Files.readString(SECURE_LOGIN);

        assertTrue(
                source.contains(".consumeSsoTicket(habbo.getHabboInfo().getId())"),
                "resuming a parked session must consume the ticket the parking flow restored");
    }

    @Test
    void ssoTokenExchangeAcceptsALiveSessionHoldingTheConsumedTicket() throws Exception {
        String source = Files.readString(SESSION_ENDPOINTS);

        int handler = source.indexOf("static void handleSsoToken(");
        assertTrue(handler >= 0, "sso-token exchange endpoint must exist");
        assertTrue(
                source.indexOf("findClientBySsoTicket(", handler) > handler,
                "the exchange races the game login that consumes the ticket; a live authenticated client with the same ticket must still be accepted");
    }
}
