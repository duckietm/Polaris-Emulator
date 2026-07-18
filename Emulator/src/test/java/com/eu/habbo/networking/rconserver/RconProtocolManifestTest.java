package com.eu.habbo.networking.rconserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RconProtocolManifestTest {
    private static final Path MANIFEST = Path.of("../protocol/rcon-contract.json");
    private static final Path SERVER = Path.of("src/main/java/com/eu/habbo/networking/rconserver/RCONServer.java");
    private static final Pattern REGISTRATION = Pattern.compile("addRCONMessage\\(\"([^\"]+)\"");

    @Test
    void manifestDefinesTheStableWireEnvelope() throws Exception {
        JsonObject manifest = readManifest();

        assertEquals(1, manifest.get("version").getAsInt());
        assertEquals(Set.of("key", "data"), keys(manifest.getAsJsonObject("request")));
        assertEquals(Set.of("status", "message"), keys(manifest.getAsJsonObject("response")));
        assertEquals("remove underscores and lowercase", manifest.get("commandNormalization").getAsString());
        assertEquals(0, manifest.getAsJsonObject("statuses").get("ok").getAsInt());
        assertEquals(1, manifest.getAsJsonObject("statuses").get("error").getAsInt());
        assertEquals(2, manifest.getAsJsonObject("statuses").get("systemError").getAsInt());
    }

    @Test
    void manifestCommandsExactlyMatchServerRegistrations() throws Exception {
        Set<String> registered = new LinkedHashSet<>();
        Matcher matcher = REGISTRATION.matcher(Files.readString(SERVER));
        while (matcher.find()) registered.add(matcher.group(1));

        JsonArray commands = readManifest().getAsJsonArray("commands");
        Set<String> documented = new LinkedHashSet<>();
        commands.forEach(command -> documented.add(command.getAsString()));

        assertEquals(commands.size(), documented.size(), "RCON manifest must not contain duplicate commands");
        assertEquals(registered, documented,
                "Every server command must be documented and removed commands must leave the manifest");
    }

    private static JsonObject readManifest() throws Exception {
        assertTrue(Files.isRegularFile(MANIFEST), "Missing versioned RCON contract: " + MANIFEST);
        return JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();
    }

    private static Set<String> keys(JsonObject object) {
        return object.keySet();
    }
}
