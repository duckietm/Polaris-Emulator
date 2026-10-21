package com.eu.habbo.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * The generator sets a packet aside as an exemption when its fields differ between the emulator and the renderer,
 * and every gate then passes. This keeps that list from growing, or going stale, without somebody deciding it.
 */
class PacketWireMismatchBaselineTest {
    private static final String MISMATCH_REASON = "wire mismatch";

    private final Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void theWireMismatchesAreTheOnesOfTheBaseline() throws Exception {
        Map<String, String> current = new TreeMap<>();

        new PacketContractManifestLoader()
                .load(repositoryRoot.resolve("protocol/packet-field-contracts.json")).exemptions().stream()
                        .filter(exemption ->
                                exemption.reason() != null && exemption.reason().contains(MISMATCH_REASON))
                        .forEach(exemption ->
                                current.put(key(exemption.direction(), exemption.header()), exemption.name()));

        Map<String, String> accepted = new TreeMap<>();
        JsonObject baseline = JsonParser.parseString(Files.readString(
                        repositoryRoot.resolve("protocol/packet-wire-mismatch-baseline.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();

        for (JsonElement entry : baseline.getAsJsonArray("mismatches")) {
            JsonObject object = entry.getAsJsonObject();

            accepted.put(
                    key(
                            object.get("direction").getAsString(),
                            object.get("header").getAsInt()),
                    object.get("name").getAsString());
        }

        Map<String, String> added = new TreeMap<>(current);
        added.keySet().removeAll(accepted.keySet());
        Map<String, String> gone = new TreeMap<>(accepted);
        gone.keySet().removeAll(current.keySet());

        assertTrue(
                added.isEmpty(),
                () -> "These packets have different fields on the emulator and the renderer: "
                        + added
                        + ". Fix the wire on one side; add a packet to protocol/packet-wire-mismatch-baseline.json "
                        + "only when the difference is accepted.");
        assertTrue(
                gone.isEmpty(),
                () -> "These packets no longer differ between the emulator and the renderer: "
                        + gone
                        + ". Remove them from protocol/packet-wire-mismatch-baseline.json.");
    }

    private static String key(String direction, int header) {
        return direction + ':' + header;
    }
}
