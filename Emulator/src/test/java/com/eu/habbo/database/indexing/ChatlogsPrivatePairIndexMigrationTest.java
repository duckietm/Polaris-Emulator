package com.eu.habbo.database.indexing;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ChatlogsPrivatePairIndexMigrationTest {
    @Test
    void indexesTheConversationPairNewestFirstWithoutDroppingAnything() throws Exception {
        String migration = Files.readString(
                Path.of("src/main/resources/db/migration/V20261028210000__chatlogs_private_pair_index.sql"));

        assertTrue(
                migration.contains(
                        "ALTER TABLE `chatlogs_private` ADD INDEX `idx_chatlogs_private_pair` (`user_from_id`, `user_to_id`, `id`)"));
        assertTrue(migration.contains("information_schema.STATISTICS"));
        assertFalse(migration.toUpperCase().contains("DROP INDEX"));
    }
}
