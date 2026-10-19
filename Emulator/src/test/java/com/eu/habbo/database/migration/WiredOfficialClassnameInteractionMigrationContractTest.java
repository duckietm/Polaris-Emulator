package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WiredOfficialClassnameInteractionMigrationContractTest {

    /** Habbo's classname to the interaction type we already register for that box. */
    private static final Map<String, String> MAPPING = Map.of(
            "wf_act_progress_ach", "wf_act_progress_achievement",
            "wf_act_set_visibility", "wf_act_change_opacity",
            "wf_trg_room_initializes", "wf_trg_room_initialized",
            "wf_var_dailytask", "wf_var_daily_task",
            "wf_ltdproto_act_toggle_state", "wf_act_toggle_state",
            "wf_proto_cnd_trggrer_on_frn", "wf_cnd_trggrer_on_frn",
            "wf_proto_trg_at_given_time", "wf_trg_at_given_time");

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                        "src/main/resources/db/migration/V20261019120000__wired_official_classname_interactions.sql"))
                .replace("\r\n", "\n");
    }

    @Test
    void everyClassnameMovesFromBothStaleTypesOntoItsInteraction() throws Exception {
        String migration = migration();

        MAPPING.forEach((classname, interaction) -> {
            assertTrue(migration.contains("('" + classname + "', 'default', '" + interaction + "')"), classname);
            assertTrue(
                    migration.contains("('" + classname + "', '" + classname + "', '" + interaction + "')"), classname);
        });
    }

    @Test
    void everyTargetInteractionIsRegistered() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java"));

        MAPPING.values()
                .forEach(interaction ->
                        assertTrue(source.contains("\"" + interaction + "\""), interaction + " is registered"));
        MAPPING.keySet()
                .forEach(classname ->
                        assertFalse(source.contains("\"" + classname + "\""), classname + " is not its own type"));
    }

    @Test
    void migrationRewritesOnlyStaleRowsMatchedByClassname() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("CONVERT(item.`item_name` USING utf8mb4) COLLATE utf8mb4_general_ci"));
        assertTrue(migration.contains("CONVERT(item.`interaction_type` USING utf8mb4) COLLATE utf8mb4_general_ci"));
        assertFalse(migration.contains("= item.`item_name`"), "no bare comparison against items_base");
        assertFalse(migration.contains("`public_name` ="));
    }
}
