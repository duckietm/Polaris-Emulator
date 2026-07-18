package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketContractCatalogGeneratorTest {
    @Test
    void acceptsAJavaPrefixWhenTypeScriptOnlyAddsTrailingOptionalFields() {
        assertTrue(PacketContractCatalogGenerator.signaturesCompatible(
                List.of("int"),
                List.of("int", "optional<bytesAvailable:[string]>")));
    }

    @Test
    void extractsTheCompleteJavaPacketInventory() throws Exception {
        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
        PacketContractCatalogGenerator generator = new PacketContractCatalogGenerator(repositoryRoot);

        var inventory = generator.javaInventory();

        assertTrue(inventory.size() > 800);
        assertTrue(inventory.stream().anyMatch(packet -> packet.header() == 412));
        assertTrue(inventory.stream().anyMatch(packet -> !packet.fields().isEmpty()));
        assertTrue(inventory.stream().anyMatch(packet -> packet.unsupportedReason() != null));

        String destination = System.getenv("PACKET_CONTRACT_JAVA_INVENTORY");
        if (destination != null && !destination.isBlank()) {
            generator.writeJavaInventory(Path.of(destination));
        }

        String typescriptInventory = System.getenv("PACKET_CONTRACT_TYPESCRIPT_INVENTORY");
        if (typescriptInventory != null && !typescriptInventory.isBlank()) {
            String json = generator.generate(Path.of(typescriptInventory));
            PacketContractManifest manifest = new PacketContractManifestLoader().parse(json);
            assertTrue(manifest.contracts().size() > 500);
            assertTrue(manifest.unpaired().size() > 100);
            assertTrue(manifest.exemptions().size() > 250);

            String emulatorManifest = System.getenv("PACKET_CONTRACT_EMULATOR_MANIFEST");
            String rendererManifest = System.getenv("PACKET_CONTRACT_RENDERER_MANIFEST");
            if (emulatorManifest != null && rendererManifest != null) {
                generator.writeManifest(
                        Path.of(typescriptInventory),
                        Path.of(emulatorManifest),
                        Path.of(rendererManifest));
            }
        }
    }
}
