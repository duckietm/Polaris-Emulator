package com.eu.habbo.database;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessengerHistorySchemaContractTest {
    private static final Path MIGRATION = Path.of("../Database/Database Updates/025_messenger_history.sql");
    private static final Path FULL_DATABASE = Path.of("../Database/Default Database/FullDatabase.sql");

    @Test
    void migrationDefinesUtf8InnoDbMessengerTablesAndIndexes() throws Exception {
        String sql = Files.readString(MIGRATION);

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_conversations`")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_members`")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_messages`")),
                () -> assertTrue(sql.contains("UNIQUE KEY `uq_messenger_direct_key` (`direct_key`)")),
                () -> assertTrue(sql.contains("KEY `idx_messenger_page` (`conversation_id`, `id`)")),
                () -> assertTrue(sql.contains("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"))
        );
    }

    @Test
    void cleanInstallSchemaMirrorsMessengerTables() throws Exception {
        String sql = Files.readString(FULL_DATABASE);

        assertAll(
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_conversations`")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_members`")),
                () -> assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS `messenger_messages`"))
        );
    }
}
