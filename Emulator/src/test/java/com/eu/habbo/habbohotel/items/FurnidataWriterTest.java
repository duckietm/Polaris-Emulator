package com.eu.habbo.habbohotel.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FurnidataWriterTest {

    private static final String SINGLE = "{ \"roomitemtypes\": { \"furnitype\": [\n"
            + "  { \"id\": 1, \"classname\": \"01_caterhead\", \"name\": \"old name\", \"description\": \"old desc\" }\n"
            + "] }, \"wallitemtypes\": { \"furnitype\": [] } }";

    // Tier data: core has the entry with "core name"; custom ALSO has it with "custom old name".
    // The writer must pick the custom (winning) tier and leave core untouched.
    private static final String CORE_DATA = "{ \"roomitemtypes\": { \"furnitype\": [\n"
            + "  { \"id\": 1, \"classname\": \"split_chair\", \"name\": \"core name\", \"description\": \"core desc\" }\n"
            + "] }, \"wallitemtypes\": { \"furnitype\": [] } }";

    private static final String CUSTOM_DATA = "{ \"roomitemtypes\": { \"furnitype\": [\n"
            + "  { \"id\": 1, \"classname\": \"split_chair\", \"name\": \"custom old name\", \"description\": \"custom old desc\" }\n"
            + "] }, \"wallitemtypes\": { \"furnitype\": [] } }";

    @Test
    void writesNameAndDescriptionByClassnameSingleFile() throws Exception {
        Path dir = Files.createTempDirectory("fd");
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);

        FurnidataWriter w = new FurnidataWriter(file, false, 64L * 1024 * 1024, 10);
        boolean ok = w.write("01_caterhead", "Cat Head", "A cat head");

        assertTrue(ok);
        String after = Files.readString(file);
        assertTrue(after.contains("\"Cat Head\""));
        assertTrue(after.contains("\"A cat head\""));
        assertFalse(after.contains("old name"));
        // backup created
        assertTrue(Files.list(dir).anyMatch(p -> p.getFileName().toString().startsWith("FurnitureData.json.bak")));
    }

    @Test
    void rejectsUnknownClassname() throws Exception {
        Path dir = Files.createTempDirectory("fd");
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);
        FurnidataWriter w = new FurnidataWriter(file, false, 64L * 1024 * 1024, 10);
        assertFalse(w.write("does_not_exist", "x", "y"));
    }

    /**
     * Split-tier: classname present in both core and custom tiers.
     * The writer must update the winning (later) tier — custom — and leave core untouched.
     */
    @Test
    void splitTierWritesWinningTierLeavesEarlierTierUntouched() throws Exception {
        Path base = Files.createTempDirectory("fd-split");

        // Tier subdirectories
        Path coreDir = base.resolve("core");
        Path customDir = base.resolve("custom");
        Files.createDirectories(coreDir);
        Files.createDirectories(customDir);

        // Top-level manifest: tiers in override order (core < custom)
        Files.writeString(base.resolve("manifest.json"), "{ \"tiers\": [ \"core\", \"custom\" ] }");

        // Per-tier manifests listing the data file
        Files.writeString(coreDir.resolve("manifest.json"), "{ \"files\": [ \"furnidata.json\" ] }");
        Files.writeString(customDir.resolve("manifest.json"), "{ \"files\": [ \"furnidata.json\" ] }");

        // Data files
        Path coreFile = coreDir.resolve("furnidata.json");
        Path customFile = customDir.resolve("furnidata.json");
        Files.writeString(coreFile, CORE_DATA);
        Files.writeString(customFile, CUSTOM_DATA);

        FurnidataWriter w = new FurnidataWriter(base, true, 64L * 1024 * 1024, 10);
        boolean ok = w.write("split_chair", "New Name", "New desc");

        assertTrue(ok, "write must succeed for classname present in split-tier layout");

        // custom (winning tier) must be updated
        String customAfter = Files.readString(customFile);
        assertTrue(customAfter.contains("\"New Name\""), "winning tier must contain new name");
        assertTrue(customAfter.contains("\"New desc\""), "winning tier must contain new desc");
        assertFalse(customAfter.contains("custom old name"), "old name must be gone from winning tier");

        // core (earlier tier) must be UNTOUCHED
        String coreAfter = Files.readString(coreFile);
        assertTrue(coreAfter.contains("core name"), "earlier tier must be left untouched");
    }

    /**
     * Split-tier path-traversal guard: a manifest that lists "../escape" as a tier
     * must be rejected by safeResolve so the writer cannot reach files outside the base dir.
     */
    @Test
    void splitTierRejectsTraversalTierInManifest() throws Exception {
        Path base = Files.createTempDirectory("fd-traversal");

        // "Escape" directory sits OUTSIDE base
        Path escapeDir = base.getParent().resolve("escape_secret");
        Files.createDirectories(escapeDir);
        Files.writeString(escapeDir.resolve("manifest.json"), "{ \"files\": [ \"secret.json\" ] }");
        Files.writeString(
                escapeDir.resolve("secret.json"),
                "{ \"roomitemtypes\": { \"furnitype\": [\n"
                        + "  { \"id\": 99, \"classname\": \"escape_chair\", \"name\": \"secret old\", \"description\": \"\" }\n"
                        + "] }, \"wallitemtypes\": { \"furnitype\": [] } }");

        // Top-level manifest references the escape dir via traversal
        Files.writeString(base.resolve("manifest.json"), "{ \"tiers\": [ \"../escape_secret\" ] }");

        FurnidataWriter w = new FurnidataWriter(base, true, 64L * 1024 * 1024, 10);
        boolean ok = w.write("escape_chair", "Pwned", "desc");

        assertFalse(ok, "classname reachable only via traversal path must not be found/written");

        // The secret file must not have been touched
        String secretAfter = Files.readString(escapeDir.resolve("secret.json"));
        assertTrue(secretAfter.contains("secret old"), "traversal target must be untouched");
    }

    @Test
    void createsJsoncFileForMissingTier(@TempDir Path base) {
        FurnidataWriter writer = new FurnidataWriter(base, true, 64L * 1024 * 1024, 10);

        FurnidataWriter.CreateResult result = writer.create(
                "new_chair",
                50,
                FurnitureType.FLOOR,
                "{\"id\":50,\"classname\":\"new_chair\",\"name\":\"New chair\",\"description\":\"\"}",
                "custom");

        assertEquals(FurnidataWriter.CreateResult.CREATED, result);
        assertTrue(Files.exists(base.resolve("custom").resolve("furnidata.jsonc")));
        assertFalse(Files.exists(base.resolve("custom").resolve("furnidata.json5")));
    }

    @Test
    void discoversJsoncManifests(@TempDir Path base) throws Exception {
        Path custom = base.resolve("custom");
        Files.createDirectories(custom);
        Files.writeString(base.resolve("manifest.jsonc"), """
            {
              // Later tiers override earlier tiers.
              "tiers": [ "custom", ],
            }
            """);
        Files.writeString(custom.resolve("manifest.jsonc"), """
            {
              "files": [ "furnidata.jsonc", ],
            }
            """);
        Path data = custom.resolve("furnidata.jsonc");
        Files.writeString(data, CUSTOM_DATA);

        FurnidataWriter writer = new FurnidataWriter(base, true, 64L * 1024 * 1024, 10);

        assertTrue(writer.write("split_chair", "JSONC name", "JSONC description"));
        assertTrue(Files.readString(data).contains("\"JSONC name\""));
    }

    @Test
    void refusesJson5SingleFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json5");
        Files.writeString(file, SINGLE);

        FurnidataWriter writer = new FurnidataWriter(file, false, 64L * 1024 * 1024, 10);

        assertFalse(writer.write("01_caterhead", "Changed", "Changed"));
        assertEquals(SINGLE, Files.readString(file));
    }

    @Test
    void ignoresJson5Manifest(@TempDir Path base) throws Exception {
        Path unsupported = base.resolve("unsupported");
        Files.createDirectories(unsupported);
        Files.writeString(base.resolve("manifest.json5"), "{ \"tiers\": [ \"unsupported\" ] }");
        Files.writeString(unsupported.resolve("manifest.json"), "{ \"files\": [ \"furnidata.json\" ] }");
        Path data = unsupported.resolve("furnidata.json");
        Files.writeString(data, CUSTOM_DATA);

        FurnidataWriter writer = new FurnidataWriter(base, true, 64L * 1024 * 1024, 10);

        assertFalse(writer.write("split_chair", "Changed", "Changed"));
        assertEquals(CUSTOM_DATA, Files.readString(data));
    }

    @Test
    void revertsJsoncBackup(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.jsonc");
        Files.writeString(file, SINGLE);
        FurnidataWriter writer = new FurnidataWriter(file, false, 64L * 1024 * 1024, 10);

        assertTrue(writer.write("01_caterhead", "Changed", "Changed"));
        assertTrue(writer.revertLastBackup());
        assertEquals(SINGLE, Files.readString(file));
    }
}
