package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Without "remember me" the access token was never renewed, so the HTTP features stopped when it expired. */
class AccessTokenRenewTest {

    @Test
    void aValidTokenOfAUserInTheHotelIsRenewed() throws Exception {
        String endpoints = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java"));
        int method = endpoints.indexOf("static void handleTokenRenew(");
        int verify = endpoints.indexOf("AccessTokenService.verify(conn, bearerToken(req));", method);
        int online = endpoints.indexOf("gameClients().getHabbo(userId) == null", verify);
        int banned = endpoints.indexOf("refuseBanned(ctx, req, conn, userId, ip, null)", online);
        int issue = endpoints.indexOf("AccessTokenService.issue(conn, userId);", banned);

        assertTrue(method > 0 && verify > method && online > verify && banned > online && issue > banned);
    }

    @Test
    void theRenewPathIsAnAuthPath() throws Exception {
        String handler =
                Files.readString(Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/AuthHttpHandler.java"));

        assertTrue(handler.contains("static final String TOKEN_RENEW_PATH = \"/api/auth/token/renew\";"));
        assertTrue(handler.contains("|| path.equals(TOKEN_RENEW_PATH)"));
        assertTrue(handler.contains("SessionEndpoints.handleTokenRenew(ctx, req, ip);"));
    }
}
