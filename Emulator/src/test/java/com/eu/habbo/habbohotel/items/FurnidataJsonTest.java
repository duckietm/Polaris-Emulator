package com.eu.habbo.habbohotel.items;

import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FurnidataJsonTest {

    @Test
    void parsesJsoncCommentsAndTrailingCommas() {
        JsonObject parsed = FurnidataJson.parseObject("""
            {
              // line comment
              "url": "https://hotel.test/a//b",
              /* block comment */
              "values": [1, 2,],
            }
            """);

        assertEquals("https://hotel.test/a//b", parsed.get("url").getAsString());
        assertEquals(2, parsed.getAsJsonArray("values").size());
    }

    @Test
    void acceptsStrictJson() {
        assertEquals(1, FurnidataJson.parseObject("{\"value\":1}").get("value").getAsInt());
    }

    @Test
    void rejectsJson5OnlySyntax() {
        assertAll(
            () -> assertThrows(
                    JsonParseException.class, () -> FurnidataJson.parseObject("{'value':1}")),
            () -> assertThrows(
                    JsonParseException.class, () -> FurnidataJson.parseObject("{value:1}")),
            () -> assertThrows(
                    JsonParseException.class, () -> FurnidataJson.parseObject("{\"value\":0x10}")),
            () -> assertThrows(
                    JsonParseException.class,
                    () -> FurnidataJson.parseObject("{\"value\":Infinity}")),
            () -> assertThrows(
                    JsonParseException.class, () -> FurnidataJson.parseObject("{\"value\":NaN}")));
    }

    @Test
    void supportsOnlyJsonAndJsoncExtensions(@TempDir Path dir) {
        assertTrue(FurnidataJson.isSupportedDocument(dir.resolve("data.json")));
        assertTrue(FurnidataJson.isSupportedDocument(dir.resolve("data.jsonc")));
        assertFalse(FurnidataJson.isSupportedDocument(dir.resolve("data.json5")));
        assertFalse(FurnidataJson.isSupportedDocument(dir.resolve("data.txt")));
    }
}
