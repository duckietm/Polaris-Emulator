package com.eu.habbo.messages.contracts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketContractManifestLoaderTest {
    private final PacketContractManifestLoader loader = new PacketContractManifestLoader();

    @Test
    void loadsScalarAndStructuredWireSchemas() {
        PacketContractManifest manifest = loader.parse("""
                {
                  "schemaVersion": 1,
                  "contracts": [{
                    "name": "FIXTURE",
                    "direction": "client_to_server",
                    "header": 4900,
                    "java": {"symbol":"FixtureEvent","className":"FixtureEvent","path":"FixtureEvent.java"},
                    "typescript": {"symbol":"FIXTURE","className":"FixtureComposer","path":"FixtureComposer.ts"},
                    "fields": [
                      {"kind":"scalar","type":"int","name":"id"},
                      {"kind":"list","countType":"short","item":[{"kind":"scalar","type":"string","name":"value"}]},
                      {"kind":"optional","controller":"enabled","fields":[{"kind":"scalar","type":"boolean","name":"active"}]},
                      {"kind":"variant","discriminator":"type","branches":{"0":[],"1":[{"kind":"scalar","type":"long","name":"stamp"}]}}
                    ]
                  }],
                  "unpaired": [],
                  "exemptions": []
                }
                """);

        assertEquals(1, manifest.schemaVersion());
        assertEquals(1, manifest.contracts().size());
        assertEquals(4, manifest.contracts().getFirst().fields().size());
        assertTrue(manifest.contracts().getFirst().fields().get(0) instanceof ScalarSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(1) instanceof ListSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(2) instanceof OptionalSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(3) instanceof VariantSchema);
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> loader.parse(emptyManifest(2)));

        assertTrue(error.getMessage().contains("schemaVersion 1"));
    }

    @Test
    void rejectsDuplicateDirectionalHeaderClassification() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> loader.parse("""
                        {
                          "schemaVersion": 1,
                          "contracts": [{
                            "name":"ONE","direction":"server_to_client","header":4900,
                            "java":{"symbol":"One","className":"One","path":"One.java"},
                            "typescript":{"symbol":"ONE","className":"One","path":"One.ts"},
                            "fields":[]
                          }],
                          "unpaired": [{
                            "direction":"server_to_client","side":"java","header":4900,
                            "symbol":"Other","path":"Other.java","reason":"Only the emulator exposes this legacy packet"
                          }],
                          "exemptions": []
                        }
                        """));

        assertTrue(error.getMessage().contains("classified more than once"));
    }

    @Test
    void rejectsUnknownScalarType() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> loader.parse("""
                        {
                          "schemaVersion": 1,
                          "contracts": [{
                            "name":"ONE","direction":"server_to_client","header":1,
                            "java":{"symbol":"One","className":"One","path":"One.java"},
                            "typescript":{"symbol":"ONE","className":"One","path":"One.ts"},
                            "fields":[{"kind":"scalar","type":"number","name":"id"}]
                          }],
                          "unpaired": [],
                          "exemptions": []
                        }
                        """));

        assertTrue(error.getMessage().contains("unknown scalar type"));
    }

    @Test
    void rejectsVagueExemptionReason() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> loader.parse("""
                        {
                          "schemaVersion": 1,
                          "contracts": [],
                          "unpaired": [],
                          "exemptions": [{
                            "name":"ONE","direction":"client_to_server","header":1,
                            "java":{"symbol":"One","className":"One","path":"One.java"},
                            "typescript":{"symbol":"ONE","className":"One","path":"One.ts"},
                            "reason":"complex packet"
                          }]
                        }
                        """));

        assertTrue(error.getMessage().contains("concrete exemption reason"));
    }

    private static String emptyManifest(int version) {
        return """
                {"schemaVersion": %d, "contracts": [], "unpaired": [], "exemptions": []}
                """.formatted(version);
    }
}
