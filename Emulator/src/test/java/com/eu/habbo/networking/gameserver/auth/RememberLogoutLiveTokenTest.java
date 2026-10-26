package com.eu.habbo.networking.gameserver.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * Logging out with a remember token named its user on the signature alone: an old token - expired,
 * rotated past or revoked - still disconnected that user and revoked their access tokens, every time it
 * was replayed.
 */
class RememberLogoutLiveTokenTest {

    private static ResultSet family(boolean exists, int revoked, long expiresAt, int version) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(exists);
        when(rs.getInt("revoked")).thenReturn(revoked);
        when(rs.getLong("expires_at")).thenReturn(expiresAt);
        when(rs.getInt("current_version")).thenReturn(version);
        return rs;
    }

    @Test
    void onlyTheLiveVersionOfAnActiveFamilyCounts() throws Exception {
        long now = 2_000_000_000L;

        assertTrue(RememberJwtService.isLiveFamily(family(true, 0, now + 60, 4), 4, now));
        assertFalse(RememberJwtService.isLiveFamily(family(true, 0, now + 60, 5), 4, now), "rotated past");
        assertFalse(RememberJwtService.isLiveFamily(family(true, 1, now + 60, 4), 4, now), "revoked");
        assertFalse(RememberJwtService.isLiveFamily(family(true, 0, now, 4), 4, now), "expired");
        assertFalse(RememberJwtService.isLiveFamily(family(false, 0, now + 60, 4), 4, now), "no such family");
    }

    @Test
    void theTokenIsCheckedBeforeAnythingIsRevoked() throws Exception {
        String source = Files.readString(
                Path.of("src/main/java/com/eu/habbo/networking/gameserver/auth/RememberJwtService.java"));
        int method = source.indexOf("public static int revokeFromToken(");
        String body = source.substring(method, source.indexOf("static boolean isLiveFamily(", method));

        int live = body.indexOf("isLiveFamily(rs, p.version, now)");
        int credential = body.indexOf("credentialBinding(p.userId, identity.passwordHash)");
        int revoke = body.indexOf("revokeFamilyById(conn, p.familyId);");

        assertTrue(credential > 0 && live > credential && revoke > live, body);
    }
}
