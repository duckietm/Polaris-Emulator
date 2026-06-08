package com.eu.habbo.messages.incoming.furnieditor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.eu.habbo.habbohotel.items.FurnidataSourceResolver;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FurniDataManagerTest {

    @Test
    void findsItemByClassnameBeforeDbId(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, """
            {
              "roomitemtypes": { "furnitype": [
                { "id": 9999, "classname": "throne", "name": "Throne", "description": "Royal seat" }
              ]},
              "wallitemtypes": { "furnitype": [] }
            }
            """);

        String json = FurniDataManager.findItemJson(file, false, 230, "throne");

        assertNotEquals("{}", json);
        assertTrue(json.contains("\"classname\":\"throne\""));
        assertTrue(json.contains("\"id\":9999"));
    }

    @Test
    void fallsBackToItemIdWhenClassnameIsMissing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("FurnitureData.json");
        Files.writeString(file, """
            {
              "roomitemtypes": { "furnitype": [
                { "id": 230, "classname": "db_only_match", "name": "DB ID Match", "description": "" }
              ]},
              "wallitemtypes": { "furnitype": [] }
            }
            """);

        String json = FurniDataManager.findItemJson(file, false, 230, "missing_classname");

        assertNotEquals("{}", json);
        assertTrue(json.contains("\"classname\":\"db_only_match\""));
    }

    @Test
    void expandsRendererConfigPlaceholders() {
        JsonObject config = JsonParser.parseString("""
            {
              "gamedata.url": "http://localhost:5173/nitro-assets/gamedata",
              "furnidata.url": "${gamedata.url}/FurnitureData.json?t=${timestamp}"
            }
            """).getAsJsonObject();

        String url = FurnidataSourceResolver.expandRendererUrl(config, "furnidata.url");

        assertEquals("http://localhost:5173/nitro-assets/gamedata/FurnitureData.json?t=${timestamp}", url);
    }

    @Test
    void mapsRendererUrlRelativeToAssetBase(@TempDir Path dir) {
        Path assetBase = dir.resolve("nitro-assets");

        FurnidataSourceResolver.Source source = FurnidataSourceResolver.toLocalSource(
            assetBase,
            "http://localhost:5173/nitro-assets/gamedata/FurnitureData.json?t=123"
        );

        assertNotNull(source);
        assertEquals(assetBase.resolve("gamedata").resolve("FurnitureData.json"), source.path());
        assertFalse(source.directory());
    }
}
