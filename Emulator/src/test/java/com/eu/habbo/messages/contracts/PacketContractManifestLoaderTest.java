package com.eu.habbo.messages.contracts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PacketContractManifestLoaderTest {
    private final PacketContractManifestLoader loader = new PacketContractManifestLoader();

    @Test
    void loadsScalarAndStructuredWireSchemas() {
        PacketContractManifest manifest = loader.parse("""
                {
                  "schemaVersion": 2,
                  "registry": {"defaults":{"origin":"official","stability":"stable"},"ranges":[],"aliases":[],"unsupported":[]},
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

        assertEquals(2, manifest.schemaVersion());
        assertEquals(1, manifest.contracts().size());
        assertEquals(4, manifest.contracts().getFirst().fields().size());
        assertTrue(manifest.contracts().getFirst().fields().get(0) instanceof ScalarSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(1) instanceof ListSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(2) instanceof OptionalSchema);
        assertTrue(manifest.contracts().getFirst().fields().get(3) instanceof VariantSchema);
    }

    @Test
    void loadsRegistryMetadataAndResolvesCustomRanges() {
        PacketContractManifest manifest = loader.parse("""
                {
                  "schemaVersion": 2,
                  "registry": {
                    "defaults": {"origin":"official","stability":"stable"},
                    "ranges": [{
                      "name":"catalog_tools","direction":"client_to_server","start":10000,"end":10099,
                      "origin":"custom","feature":"catalog_studio","stability":"stable"
                    }],
                    "aliases": [{
                      "side":"typescript","direction":"client_to_server","header":10050,
                      "canonical":"CATALOG_SAVE","aliases":["LEGACY_CATALOG_SAVE"],
                      "reason":"Legacy public symbol retained for renderer source compatibility"
                    }],
                    "unsupported": [{
                      "side":"java","direction":"server_to_client","symbol":"UnknownComposer",
                      "reason":"Wire header is not known for the supported client revision"
                    }]
                  },
                  "contracts": [],
                  "unpaired": [{
                    "direction":"client_to_server","side":"typescript","header":10050,
                    "symbol":"CATALOG_SAVE","path":"CatalogSave.ts",
                    "reason":"Only the renderer exposes this catalog administration packet"
                  }],
                  "exemptions": []
                }
                """);

        PacketMetadata official = manifest.registry().metadataFor("server_to_client", 400);
        PacketMetadata custom = manifest.registry().metadataFor("client_to_server", 10050);

        assertEquals("official", official.origin());
        assertEquals("stable", official.stability());
        assertEquals("catalog_tools", custom.range());
        assertEquals("custom", custom.origin());
        assertEquals("catalog_studio", custom.feature());
        assertEquals(1, manifest.registry().aliases().size());
        assertEquals(1, manifest.registry().unsupported().size());
    }

    @Test
    void rejectsOverlappingRegistryRangesInTheSameDirection() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> loader.parse(registryManifest("""
                        [
                          {"name":"one","direction":"client_to_server","start":9300,"end":9399,
                           "origin":"custom","feature":"one","stability":"stable"},
                          {"name":"two","direction":"client_to_server","start":9350,"end":9450,
                           "origin":"custom","feature":"two","stability":"experimental"}
                        ]
                        """, "[]")));

        assertTrue(error.getMessage().contains("overlapping client_to_server registry ranges"));
    }

    @Test
    void rejectsAliasesWhoseHeaderIsNotClassified() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> loader.parse(registryManifest("[]", """
                        [{
                          "side":"typescript","direction":"server_to_client","header":9999,
                          "canonical":"CANONICAL","aliases":["COMPATIBILITY"],
                          "reason":"Compatibility name retained for existing renderer integrations"
                        }]
                        """)));

        assertTrue(error.getMessage().contains("alias header server_to_client:9999 is not classified"));
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> loader.parse(emptyManifest(3)));

        assertTrue(error.getMessage().contains("schemaVersion 2"));
    }

    @Test
    void rejectsDuplicateDirectionalHeaderClassification() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> loader.parse("""
                        {
                          "schemaVersion": 2,
                          "registry": {"defaults":{"origin":"official","stability":"stable"},"ranges":[],"aliases":[],"unsupported":[]},
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
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> loader.parse("""
                        {
                          "schemaVersion": 2,
                          "registry": {"defaults":{"origin":"official","stability":"stable"},"ranges":[],"aliases":[],"unsupported":[]},
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
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> loader.parse("""
                        {
                          "schemaVersion": 2,
                          "registry": {"defaults":{"origin":"official","stability":"stable"},"ranges":[],"aliases":[],"unsupported":[]},
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
                {"schemaVersion": %d,
                 "registry":{"defaults":{"origin":"official","stability":"stable"},"ranges":[],"aliases":[],"unsupported":[]},
                 "contracts": [], "unpaired": [], "exemptions": []}
                """.formatted(version);
    }

    private static String registryManifest(String ranges, String aliases) {
        return """
                {
                  "schemaVersion":2,
                  "registry":{
                    "defaults":{"origin":"official","stability":"stable"},
                    "ranges":%s,
                    "aliases":%s,
                    "unsupported":[]
                  },
                  "contracts":[],"unpaired":[],"exemptions":[]
                }
                """.formatted(ranges, aliases);
    }
}
