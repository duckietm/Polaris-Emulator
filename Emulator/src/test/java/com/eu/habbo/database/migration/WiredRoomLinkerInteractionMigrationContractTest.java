package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WiredRoomLinkerInteractionMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(
                        Path.of("src/main/resources/db/migration/V20260923210000__wired_room_linker_interaction.sql"))
                .replace("\r\n", "\n");
    }

    @Test
    void migrationMovesTheRoomLinkerOntoItsOwnInteraction() throws Exception {
        assertTrue(migration().contains("('wf_room_linker', 'wf_room_linker')"));
    }

    @Test
    void migrationRewritesOnlyDivergingRowsMatchedByClassname() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
        assertTrue(
                migration.contains("WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci\n"
                        + "    <> mapping.`interaction_type`;"));
        assertFalse(migration.contains("= item.`item_name`"), "no bare comparison against items_base");
    }

    @Test
    void migrationLeavesTheOperatorVisibleNameAlone() throws Exception {
        assertFalse(migration().contains("`public_name` ="));
    }
}
