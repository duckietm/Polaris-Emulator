package com.eu.habbo.networking.gameserver.badges;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BadgeCorsContractTest {
    @Test
    void customBadgeApiDoesNotReflectUnapprovedOrigins() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/eu/habbo/networking/gameserver/badges/BadgeHttpHandler.java"));

        int applyCors = source.indexOf("private static void applyCors");
        int originRead = source.indexOf("get(HttpHeaderNames.ORIGIN)", applyCors);
        int allowlist = source.indexOf("CorsOriginGate.isAllowed(req)", originRead);
        int reflection = source.indexOf("set(\"Access-Control-Allow-Origin\", origin)", allowlist);

        assertTrue(originRead > applyCors);
        assertTrue(allowlist > originRead, "badge CORS must consult the configured origin gate");
        assertTrue(reflection > allowlist, "origin may only be reflected after allowlist approval");

        String corsMethod = source.substring(applyCors, source.indexOf("private static boolean isKeepAlive", applyCors));
        assertFalse(corsMethod.contains("if (origin != null && !origin.isEmpty()) {"),
                "non-empty Origin alone must not authorize credentialed CORS");
    }
}
