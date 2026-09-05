package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class WiredInteractionReapplyMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(
                Path.of("src/main/resources/db/migration/V20260905170000__reapply_wired_interaction_repair.sql"));
    }

    @Test
    void migrationBringsTheAntennaBackToTheTypeTheSignalPathRecognises() throws Exception {
        String migration = migration();

        // WiredEffectSendSignal and WiredTriggerReceiveSignal compare the interaction
        // type to "antenna" exactly, so any other value makes every signal chain silent.
        assertTrue(migration.contains("('wf_antenna1', 'default', 'antenna')"));
        assertTrue(migration.contains("('wf_antenna2', 'default', 'antenna')"));
    }

    @Test
    void migrationRepairsOnlyTheStaleStateItRecorded() throws Exception {
        String migration = migration();

        // Matching the stale value is what keeps this from overwriting an interaction a
        // hotel chose for itself: a row holding some third value is left alone.
        assertTrue(migration.contains("`stale_interaction_type`"));
        assertTrue(
                migration.contains("WHERE CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci\n"
                        + "    = mapping.`stale_interaction_type`;"));
    }

    @Test
    void everyRowRestoresSomethingOtherThanTheStaleValue() throws Exception {
        Matcher rows = Pattern.compile("[(]'([^']+)', '([^']+)', '([^']+)'[)]").matcher(migration());

        int counted = 0;
        while (rows.find()) {
            counted++;
            assertFalse(
                    rows.group(2).equals(rows.group(3)), "row " + rows.group(1) + " restores the value it calls stale");
            assertFalse("default".equals(rows.group(3)), "row " + rows.group(1) + " restores the inert default");
        }
        assertEquals(43, counted);
    }

    @Test
    void migrationPinsTheCollationOnEveryComparison() throws Exception {
        String migration = migration();

        assertFalse(migration.contains("= item.`item_name`"));
        assertTrue(migration.contains("CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
    }
}
