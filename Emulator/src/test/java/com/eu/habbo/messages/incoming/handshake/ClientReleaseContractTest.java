package com.eu.habbo.messages.incoming.handshake;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientReleaseContractTest {
    @Test
    void releaseGuardMatchesExactConfiguredVersions() throws Exception {
        Class<?> guard = assertDoesNotThrow(() -> Class.forName(
                "com.eu.habbo.messages.incoming.handshake.ClientReleaseGuard"));
        Method isAllowed = guard.getDeclaredMethod("isAllowed", String.class, String.class);
        isAllowed.setAccessible(true);

        assertTrue((boolean) isAllowed.invoke(null, "NITRO-3-6-0", "NITRO-3-6-0"));
        assertTrue((boolean) isAllowed.invoke(null, "NITRO-3-6-0", "LEGACY-1,NITRO-3-6-0"));
        assertFalse((boolean) isAllowed.invoke(null, "NITRO-3-5-0", "NITRO-3-6-0"));
        assertFalse((boolean) isAllowed.invoke(null, "", "NITRO-3-6-0"));
        assertFalse((boolean) isAllowed.invoke(null, "NITRO-3-6-0\nFORGED", "NITRO-3-6-0"));
    }

    @Test
    void releaseIsStoredBeforeSecureLoginAndCheckedBeforeSsoLookup() throws Exception {
        String release = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/handshake/ReleaseVersionEvent.java"));
        String login = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));
        String client = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/habbohotel/gameclients/GameClient.java"));

        assertTrue(release.contains("setReleaseVersion"));
        assertTrue(client.contains("private String releaseVersion"));

        int releaseCheck = login.indexOf("ClientReleaseGuard.isAllowed");
        int ssoRead = login.indexOf("this.packet.readString()");
        assertTrue(releaseCheck > -1 && releaseCheck < ssoRead,
                "client release must be accepted before the SSO ticket is consumed");
    }
}
