package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketContractCoverageTest {
    private final Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();

    @Test
    void classifiesEveryActiveEmulatorHeaderExactlyOnce() throws Exception {
        PacketContractManifest manifest = new PacketContractManifestLoader().load(
                repositoryRoot.resolve("protocol/packet-field-contracts.json"));
        JavaPacketRegistry registry = JavaPacketRegistry.discover(
                repositoryRoot.resolve("Emulator/src/main/java"));
        Set<String> classified = classified(manifest);
        Set<String> active = new HashSet<>();
        registry.active().forEach(packet -> active.add(key(packet.direction().manifestName(), packet.header())));

        assertEquals(active, active.stream().filter(classified::contains).collect(java.util.stream.Collectors.toSet()),
                () -> "unclassified emulator headers: " + active.stream().filter(key -> !classified.contains(key)).toList());
    }

    @Test
    void referencesOnlyExistingEmulatorSources() throws Exception {
        PacketContractManifest manifest = new PacketContractManifestLoader().load(
                repositoryRoot.resolve("protocol/packet-field-contracts.json"));
        manifest.contracts().forEach(contract -> assertTrue(Files.isRegularFile(repositoryRoot.resolve(contract.java().path())),
                contract.java().path()));
        manifest.exemptions().forEach(exemption -> assertTrue(Files.isRegularFile(repositoryRoot.resolve(exemption.java().path())),
                exemption.java().path()));
        manifest.unpaired().stream().filter(packet -> packet.side().equals("java")).forEach(packet ->
                assertTrue(Files.isRegularFile(repositoryRoot.resolve(packet.path())), packet.path()));
    }

    private static Set<String> classified(PacketContractManifest manifest) {
        Set<String> result = new HashSet<>();
        manifest.contracts().forEach(entry -> result.add(key(entry.direction(), entry.header())));
        manifest.exemptions().forEach(entry -> result.add(key(entry.direction(), entry.header())));
        manifest.unpaired().forEach(entry -> result.add(key(entry.direction(), entry.header())));
        return result;
    }

    private static String key(String direction, int header) {
        return direction + ':' + header;
    }
}
