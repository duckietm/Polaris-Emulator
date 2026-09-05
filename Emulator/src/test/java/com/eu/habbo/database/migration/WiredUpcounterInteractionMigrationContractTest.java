package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class WiredUpcounterInteractionMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/db/migration/V20260905120000__repair_wired_upcounter_interactions.sql"));
    }

    @Test
    void migrationMovesBothUpcountersOntoTheCountingInteraction() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("('wf_upcounter1', 'game_upcounter')"));
        assertTrue(migration.contains("('wf_upcounter2', 'game_upcounter')"));
        // `counter` is the ordinary kitchen-counter furniture shared by a hundred
        // decorative items and backed by no interaction class: nothing may be sent there.
        assertFalse(migration.contains("'counter')"));
    }

    @Test
    void migrationRewritesOnlyDivergingRowsSoAHotelKeepsWhatIsAlreadyRight() throws Exception {
        String migration = migration();

        assertTrue(
                migration.contains("WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci\n"
                        + "    <> mapping.`interaction_type`;"));
        // The classname is the stable identity, so a public_name match must stand down
        // whenever item_name already named the same row.
        assertTrue(migration.contains("WHERE item_name_mapping.`item_identifier` IS NULL"));
    }

    @Test
    void migrationPinsTheCollationOnEveryComparison() throws Exception {
        String migration = migration();

        // Adopted hotels carry latin1 or either utf8mb4 collation on items_base; a bare
        // column-to-column comparison raises "Illegal mix of collations" on some of them.
        assertFalse(migration.contains("= item.`item_name`"));
        assertFalse(migration.contains("= item.`public_name`"));
        assertTrue(migration.contains("CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
        assertTrue(migration.contains("CONVERT(item.`public_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
    }

    @Test
    void migrationLeavesTheOperatorVisibleNameAlone() throws Exception {
        assertFalse(migration().contains("`public_name` ="));
    }
}
