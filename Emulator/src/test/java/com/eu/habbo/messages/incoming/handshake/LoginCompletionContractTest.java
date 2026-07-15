package com.eu.habbo.messages.incoming.handshake;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginCompletionContractTest {
    @Test
    void runsLegacyLoginCompletionFromTheAuthenticatedLoginFlow() throws Exception {
        String secureLogin = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/handshake/SecureLoginEvent.java"));
        String completion = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/messages/incoming/handshake/UsernameEvent.java"));

        assertTrue(secureLogin.contains("UsernameEvent.completeLogin(this.client)"));
        assertTrue(completion.contains("public static void completeLogin(GameClient client)"));
    }
}
