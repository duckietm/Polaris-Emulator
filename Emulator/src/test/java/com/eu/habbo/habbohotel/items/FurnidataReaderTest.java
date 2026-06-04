package com.eu.habbo.habbohotel.items;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FurnidataReaderTest {

    private static final String SINGLE = """
        {
          // a comment
          "roomitemtypes": { "furnitype": [
            { "id": 10, "classname": "chair_norja", "name": "Chair", "description": "Sit", "xdim": 1, "ydim": 1 },
          ]},
          "wallitemtypes": { "furnitype": [
            { "id": 20, "classname": "poster_5", "name": "Poster", "description": "Wall" }
          ]}
        }
        """;

    @Test
    void parsesSingleFileFloorAndWall(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);

        List<FurnidataEntry> entries = new FurnidataReader(file, 64 * 1024 * 1024).read();

        assertEquals(2, entries.size());
        FurnidataEntry floor = entries.stream().filter(e -> e.id() == 10).findFirst().orElseThrow();
        assertEquals("chair_norja", floor.classname());
        assertEquals(FurnitureType.FLOOR, floor.type());
        assertEquals("Chair", floor.name());
        FurnidataEntry wall = entries.stream().filter(e -> e.id() == 20).findFirst().orElseThrow();
        assertEquals(FurnitureType.WALL, wall.type());
    }

    @Test
    void rejectsFileOverSizeCap(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, SINGLE);
        List<FurnidataEntry> entries = new FurnidataReader(file, 8 /* bytes */).read();
        assertTrue(entries.isEmpty(), "oversized file must be refused, returning empty");
    }

    @Test
    void missingSourceReturnsEmptyNeverThrows(@TempDir Path dir) {
        Path missing = dir.resolve("does-not-exist.json");
        assertDoesNotThrow(() -> {
            assertTrue(new FurnidataReader(missing, 64 * 1024 * 1024).read().isEmpty());
        });
    }

    @Test
    void oversizedManifestIsSkippedNeverThrows(@TempDir Path dir) throws Exception {
        Path base = dir.resolve("furnidata");
        Path core = base.resolve("core");
        Files.createDirectories(core);
        // A root manifest larger than the cap we pass in.
        Files.writeString(base.resolve("manifest.json"), "{ \"tiers\": [ \"core\" ] }   // padding ".repeat(50));
        List<FurnidataEntry> entries = new FurnidataReader(base, 8 /* bytes */).read();
        assertTrue(entries.isEmpty());
    }

    @Test
    void splitDirRejectsTraversalFiles(@TempDir Path dir) throws Exception {
        Path secret = dir.resolve("secret.json");
        Files.writeString(secret, "{ \"roomitemtypes\": { \"furnitype\": [ { \"id\": 99, \"classname\": \"x\", \"name\": \"LEAK\", \"description\": \"\" } ] } }");

        Path base = dir.resolve("furnidata");
        Path core = base.resolve("core");
        Files.createDirectories(core);
        Files.writeString(base.resolve("manifest.json"), "{ \"tiers\": [ \"core\" ] }");
        Files.writeString(core.resolve("manifest.json"), "{ \"files\": [ \"../../secret.json\" ] }");

        List<FurnidataEntry> entries = new FurnidataReader(base, 64 * 1024 * 1024).read();

        assertTrue(entries.stream().noneMatch(e -> e.id() == 99),
            "traversal file outside the base dir must be ignored");
    }
}
