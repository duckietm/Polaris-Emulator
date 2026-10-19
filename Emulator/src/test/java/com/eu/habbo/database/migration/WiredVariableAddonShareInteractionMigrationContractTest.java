package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WiredVariableAddonShareInteractionMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                        "src/main/resources/db/migration/V20261019100100__wired_variable_addon_share_interactions.sql"))
                .replace("\r\n", "\n");
    }

    @Test
    void migrationMovesBothBoxesFromDefaultOntoTheirInteractions() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("('wf_xtra_var_share', 'default', 'wf_xtra_var_share')"));
        assertTrue(migration.contains("('wf_xtra_var_import', 'default', 'wf_xtra_var_import')"));
    }

    @Test
    void migrationRewritesOnlyStaleRowsMatchedByClassname() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
        assertTrue(
                migration.contains("WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci\n"
                        + "    = mapping.`stale_interaction_type`;"));
        assertFalse(migration.contains("= item.`item_name`"), "no bare comparison against items_base");
    }

    @Test
    void migrationLeavesTheOperatorVisibleNameAlone() throws Exception {
        assertFalse(migration().contains("`public_name` ="));
    }
}
