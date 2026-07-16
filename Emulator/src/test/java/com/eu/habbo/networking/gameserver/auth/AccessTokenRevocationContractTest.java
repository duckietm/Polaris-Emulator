package com.eu.habbo.networking.gameserver.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessTokenRevocationContractTest {
    @Test
    void tokensCarryAndVerifyTheUsersCurrentVersion() throws Exception {
        String access = read("src/main/java/com/eu/habbo/networking/gameserver/auth/AccessTokenService.java");

        int issueState = access.indexOf("UserSecurityState securityState = currentSecurityState(conn, userId)");
        int claim = access.indexOf("payload.addProperty(\"ver\", securityState.version)", issueState);
        int verifyVersion = access.indexOf("version != securityState.version", claim);

        assertTrue(issueState > -1, "issuance must load the persisted user security state");
        assertTrue(claim > issueState, "issued access token must contain that version");
        assertTrue(verifyVersion > claim, "verification must reject a stale version");
    }

    @Test
    void tokensAreBoundToCurrentPasswordWithoutCmsChanges() throws Exception {
        String access = read("src/main/java/com/eu/habbo/networking/gameserver/auth/AccessTokenService.java");
        String remember = read("src/main/java/com/eu/habbo/networking/gameserver/auth/RememberJwtService.java");

        assertTrue(access.contains("credentialBinding(userId, securityState.passwordHash)"),
                "access tokens must be invalidated automatically when the stored password changes");
        assertTrue(remember.contains("parsed.credential.equals(credentialBinding(parsed.userId, identity.passwordHash))"),
                "remember tokens must be invalidated automatically when the stored password changes");
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
