package polaris.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PacketContractCatalogGeneratorTest {
    @Test
    void acceptsAJavaPrefixWhenTypeScriptOnlyAddsTrailingOptionalFields() {
        assertTrue(PacketContractCatalogGenerator.signaturesCompatible(
                List.of("int"), List.of("int", "optional<bytesAvailable:[string]>")));
    }

    @Test
    void writesNestedTypeScriptFieldsInTheJavaSignatureText() {
        PacketContractCatalogGenerator generator = new PacketContractCatalogGenerator(
                Path.of("..").toAbsolutePath().normalize());

        // An optional in the middle of a packet (HOUSEKEEPING_SEND_HOTEL_ALERT) used to be compared as raw JSON
        // against Java's optional<...> text, so the packet was reported as a wire mismatch.
        assertEquals(
                generator.signature(new OptionalSchema("recipient", List.of(new ScalarSchema("string", "recipient")))),
                generator.signature(JsonParser.parseString("{\"type\":\"optional\",\"controller\":\"recipient\","
                                + "\"fields\":[{\"type\":\"string\",\"name\":\"recipient\"}]}")
                        .getAsJsonObject()));
        assertEquals(
                generator.signature(new ListSchema("int", List.of(new ScalarSchema("int", "id")))),
                generator.signature(JsonParser.parseString(
                                "{\"type\":\"list\",\"countType\":\"int\",\"item\":[{\"type\":\"int\",\"name\":\"x\"}]}")
                        .getAsJsonObject()));
        assertEquals(
                generator.signature(new VariantSchema(
                        "kind",
                        Map.of(
                                "1", List.of(new ScalarSchema("int", "a")),
                                "2", List.of(new ScalarSchema("string", "b"))))),
                generator.signature(JsonParser.parseString("{\"type\":\"variant\",\"discriminator\":\"kind\","
                                + "\"branches\":{\"2\":[{\"type\":\"string\"}],\"1\":[{\"type\":\"int\"}]}}")
                        .getAsJsonObject()));
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
                        Path.of(typescriptInventory), Path.of(emulatorManifest), Path.of(rendererManifest));
            }
        }
    }
}
