package com.eu.habbo.database.indexing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LoginPathIndexMigrationTest {
    @Test
    void indexesTheBotAndOfflineMessageLookupsWithoutDroppingAnything() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20261019110000__bots_and_offline_messages_indexes.sql"));

        assertTrue(migration.contains("ALTER TABLE `bots` ADD INDEX `idx_bots_room_id` (`room_id`)"));
        assertTrue(migration.contains("ALTER TABLE `bots` ADD INDEX `idx_bots_user_room` (`user_id`, `room_id`)"));
        assertTrue(migration.contains(
                "ALTER TABLE `messenger_offline` ADD INDEX `idx_messenger_offline_user_id` (`user_id`, `id`)"));
        assertTrue(migration.contains("information_schema.STATISTICS"));
        assertFalse(migration.toUpperCase().contains("DROP INDEX"));
    }
}
