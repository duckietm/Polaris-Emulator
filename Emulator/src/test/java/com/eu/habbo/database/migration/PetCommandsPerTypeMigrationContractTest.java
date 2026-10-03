package com.eu.habbo.database.migration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PetCommandsPerTypeMigrationContractTest {

    private static String migration() throws Exception {
        return Files.readString(Path.of("src/main/resources/db/migration/V20260924210000__pet_commands_per_type.sql"))
                .replace("\r\n", "\n");
    }

    @Test
    void migrationGivesThePterodactylItsCommands() throws Exception {
        assertTrue(migration()
                .contains("(33, 0), (33, 2), (33, 3), (33, 4), (33, 6), (33, 7), (33, 11), (33, 13), (33, 14),"
                        + " (33, 15), (33, 16), (33, 25), (33, 26), (33, 43)"));
    }

    @Test
    void migrationCoversEveryPetTypeOfTheBaseSchema() throws Exception {
        String migration = migration();

        for (int type = 0; type <= 35; type++) {
            assertTrue(migration.contains("(" + type + ", 0)"), "pet type " + type + " has no commands");
        }
    }

    @Test
    void migrationAddsTheKeyOnlyWhenMissingAndKeepsOneRowPerPair() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("CONSTRAINT_TYPE = 'PRIMARY KEY'"));
        assertTrue(migration.contains("'ALTER IGNORE TABLE `pet_commands` ADD PRIMARY KEY (`pet_id`, `command_id`)'"));
    }

    @Test
    void migrationInsertsOnlyKnownCommandsAndNeverRemovesAHotelsRows() throws Exception {
        String migration = migration();

        assertTrue(migration.contains("INSERT IGNORE INTO `pet_commands`"));
        assertTrue(migration.contains("INNER JOIN `pet_commands_data` AS command"));
        assertFalse(migration.contains("DELETE FROM `pet_commands`"));
        assertFalse(migration.contains("TRUNCATE"));
    }
}
