package com.eu.habbo.networking.rconserver;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RconAuthenticationContractTest {
    @Test
    void authenticationRunsBeforeCommandDispatchAndLegacyModeIsExplicit() throws Exception {
        String handler = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/rconserver/RCONServerHandler.java"));
        String authenticator = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/rconserver/RconRequestAuthenticator.java"));

        int verify = handler.indexOf("RconRequestAuthenticator.verify(");
        int dispatch = handler.indexOf("getRconServer().handle(", verify);

        assertTrue(verify > -1 && dispatch > verify,
                "signed request verification must occur before privileged command dispatch");
        assertTrue(authenticator.contains("if (secret.isBlank())"),
                "an empty secret must retain legacy request compatibility");
        assertTrue(authenticator.contains("SEEN_NONCES.asMap().putIfAbsent"),
                "authenticated mode must reject nonce replay");
        assertTrue(authenticator.contains("MessageDigest.isEqual"),
                "signature comparison must be constant time");
    }
}
