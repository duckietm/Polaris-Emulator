package com.eu.habbo.networking.gameserver.cms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CmsApiContractTest {
    private static final Path CMS = Path.of("../protocol/cms-api-contract.json");
    private static final Path RCON = Path.of("../protocol/rcon-contract.json");
    private static final Set<String> STRESS = Set.of("stressstart", "stressstatus", "stressstop");

    @Test
    void envelopeMatchesTheSharedCommandContract() throws Exception {
        JsonObject cms = read(CMS);

        assertEquals(1, cms.get("version").getAsInt());
        assertEquals("http-json", cms.get("transport").getAsString());
        assertEquals(Set.of("key", "data"), cms.getAsJsonObject("request").keySet());
        assertEquals(
                Set.of("status", "message"), cms.getAsJsonObject("response").keySet());
        assertEquals(
                "remove underscores and lowercase",
                cms.get("commandNormalization").getAsString());

        JsonObject statuses = cms.getAsJsonObject("statuses");
        assertEquals(0, statuses.get("ok").getAsInt());
        assertEquals(1, statuses.get("error").getAsInt());
        assertEquals(2, statuses.get("habboNotFound").getAsInt());
        assertEquals(3, statuses.get("roomNotFound").getAsInt());
        assertEquals(4, statuses.get("systemError").getAsInt());
    }

    @Test
    void hmacAuthHeadersAreDocumented() throws Exception {
        JsonObject auth = read(CMS).getAsJsonObject("auth");
        assertEquals("hmac-sha256", auth.get("scheme").getAsString());
        Set<String> headers = new LinkedHashSet<>();
        auth.getAsJsonArray("headers").forEach(h -> headers.add(h.getAsString()));
        assertEquals(Set.of("X-Cms-Key", "X-Cms-Timestamp", "X-Cms-Nonce", "X-Cms-Signature"), headers);
    }

    @Test
    void cmsCommandsAreTheRconCommandsMinusStress() throws Exception {
        Set<String> rconCommands = commandSet(read(RCON));
        Set<String> expected = new LinkedHashSet<>(rconCommands);
        expected.removeAll(STRESS);

        Set<String> cmsCommands = commandSet(read(CMS));

        assertEquals(
                expected,
                cmsCommands,
                "CMS command list must mirror the shared registry (RCON commands minus the stress commands)");
        for (String stress : STRESS) {
            assertFalse(cmsCommands.contains(stress), "Stress command must not be exposed over the CMS API: " + stress);
        }
    }

    @Test
    void alwaysDeniedMatchesTheStressCommands() throws Exception {
        Set<String> denied = new LinkedHashSet<>();
        read(CMS).getAsJsonArray("alwaysDenied").forEach(d -> denied.add(d.getAsString()));
        assertEquals(STRESS, denied);
        assertEquals(
                CmsCommandScopes.ALWAYS_DENIED,
                denied,
                "Contract alwaysDenied must match CmsCommandScopes.ALWAYS_DENIED");
    }

    @Test
    void everyExposedCommandHasAScopeGroup() throws Exception {
        for (String command : commandSet(read(CMS))) {
            assertTrue(
                    CmsCommandScopes.groupFor(command) != null,
                    "Exposed CMS command is missing a scope group in CmsCommandScopes: " + command);
        }
    }

    private static Set<String> commandSet(JsonObject contract) {
        JsonArray commands = contract.getAsJsonArray("commands");
        Set<String> set = new LinkedHashSet<>();
        commands.forEach(command -> set.add(command.getAsString()));
        assertEquals(commands.size(), set.size(), "Contract must not contain duplicate commands");
        return set;
    }

    private static JsonObject read(Path path) throws Exception {
        assertTrue(Files.isRegularFile(path), "Missing contract: " + path);
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
