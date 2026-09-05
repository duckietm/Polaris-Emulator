package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WiredFurniIdentityMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/db/migration/V20260905180000__restore_wired_furni_identity.sql"));
    }

    @Test
    void everyRowMapsAFurniOntoTheInteractionNamedAfterIt() throws Exception {
        Matcher rows = Pattern.compile("[(]'([^']+)', '([^']+)'[)]").matcher(migration());

        int counted = 0;
        while (rows.find()) {
            counted++;
            assertEquals(
                    rows.group(1),
                    rows.group(2),
                    "a wired furni may only be pointed at the interaction it is named after");
        }
        assertEquals(51, counted);
    }

    @Test
    void migrationCarriesTheFamiliesThatACatalogueImportFlattens() throws Exception {
        String migration = migration();

        // The variable, contract, chest and highscore boxes are the ones a dump from a
        // hotel without these implementations brings back as `default`.
        assertTrue(migration.contains("('wf_var_user', 'wf_var_user')"));
        assertTrue(migration.contains("('wf_act_change_var_val', 'wf_act_change_var_val')"));
        assertTrue(migration.contains("('wf_contract_reward', 'wf_contract_reward')"));
        assertTrue(migration.contains("('wf_cnd_chest_has_items', 'wf_cnd_chest_has_items')"));
        assertTrue(migration.contains("('wf_act_give_points_highscore', 'wf_act_give_points_highscore')"));
    }

    @Test
    void migrationTouchesOnlyRowsLeftOnTheDefaultInteraction() throws Exception {
        String migration = migration();

        // A hotel that deliberately pointed one of these classnames at another registered
        // class is expressing a choice, and keeps it.
        assertTrue(migration.contains(
                "WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci = 'default';"));
        assertFalse(migration.contains("= item.`item_name`"));
    }
}
