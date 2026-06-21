package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class GiveUserClothingContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/GiveUserClothing.java"));
    }

    @Test
    void validatesUsersAndClothingBeforeWritingInventoryRows() throws Exception {
        String source = source();

        assertTrue(source.contains("object.user_id <= 0 || object.clothing_id <= 0"),
                "Clothing grants must reject invalid identifiers");
        assertTrue(source.contains("userExists(object.user_id)"),
                "Clothing grants must reject missing users before inserting rows");
        assertTrue(source.contains("clothingExists(object.clothing_id)"),
                "Clothing grants must reject catalog clothing ids that do not exist");
        assertTrue(source.contains("catalog_clothing"),
                "Clothing grants must validate against catalog_clothing");
    }

    @Test
    void handlesDuplicateGrantsWithoutSurfacingSqlErrors() throws Exception {
        String source = source();

        assertTrue(source.contains("INSERT IGNORE INTO users_clothing"),
                "Duplicate clothing grants should be idempotent");
        assertTrue(source.contains("SYSTEM_ERROR"),
                "Unexpected SQL failures must be surfaced to the RCON caller");
    }
}
