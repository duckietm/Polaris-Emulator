package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenRevocationContractTest {
    @Test
    void tokensCarryAndVerifyTheUsersCurrentVersion() throws Exception {
        String access = read("src/main/java/com/eu/habbo/networking/gameserver/auth/AccessTokenService.java");

        int issueVersion = access.indexOf("long version = currentVersion(conn, userId)");
        int claim = access.indexOf("payload.addProperty(\"ver\", version)", issueVersion);
        int verifyVersion = access.indexOf("version != currentVersion(conn, userId)", claim);

        assertTrue(issueVersion > -1, "issuance must load the persisted user version");
        assertTrue(claim > issueVersion, "issued access token must contain that version");
        assertTrue(verifyVersion > claim, "verification must reject a stale version");
    }

    @Test
    void logoutRevokesTokensForEverySupportedCredential() throws Exception {
        String session = read("src/main/java/com/eu/habbo/networking/gameserver/auth/SessionEndpoints.java");

        int remember = session.indexOf("RememberJwtService.revokeFromToken");
        int ssoLookup = session.indexOf("SsoTicketPolicy.resolve(conn, ssoTicket)", remember);
        int bearer = session.indexOf("AccessTokenService.verify(conn, bearerToken(req))", ssoLookup);
        int revoke = session.indexOf("AccessTokenService.revokeAll(conn, userId)", bearer);

        assertTrue(remember > -1, "logout must resolve and revoke a supplied remember family");
        assertTrue(ssoLookup > remember, "logout must also resolve a supplied SSO identity");
        assertTrue(bearer > ssoLookup, "logout must also accept the current bearer identity");
        assertTrue(revoke > bearer, "logout must increment the resolved users access-token version");
    }

    @Test
    void passwordChangesRevokeAccessAndRememberCredentials() throws Exception {
        String account = read("src/main/java/com/eu/habbo/networking/gameserver/auth/AccountChangeEndpoints.java");

        int passwordHandler = account.indexOf("handleChangePassword(");
        int version = account.indexOf("access_token_version = access_token_version + 1", passwordHandler);
        int remember = account.indexOf("RememberJwtService.revokeAllForUser(conn, userId)", version);
        int commit = account.indexOf("conn.commit()", remember);

        assertTrue(version > passwordHandler, "password change must invalidate existing access JWTs");
        assertTrue(remember > version, "password change must prevent remember tokens from minting replacements");
        assertTrue(commit > remember, "password and credential revocation must commit together");
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
