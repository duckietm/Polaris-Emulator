package com.eu.habbo.habbohotel.messenger.history;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessengerHistoryRetentionSqlContractTest {
    @Test
    void overflowCleanupAvoidsVersionSensitiveWindowAndInLimitSyntax() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/habbohotel/messenger/history/JdbcMessengerHistoryRepository.java"));

        assertTrue(source.contains("DELETE messages FROM messenger_messages messages"));
        assertTrue(source.contains("JOIN messenger_messages newer"));
        assertTrue(source.contains("HAVING COUNT(newer.id) >= ?"));
        assertFalse(source.contains("ROW_NUMBER()"));
        assertFalse(source.contains("WHERE id IN"));
    }
}
