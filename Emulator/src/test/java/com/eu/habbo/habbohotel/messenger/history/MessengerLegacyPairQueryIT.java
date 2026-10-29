package com.eu.habbo.habbohotel.messenger.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.eu.habbo.database.TestDatabase;
import com.eu.habbo.database.migration.MigrationRunner;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The legacy reads of one conversation used an OR across both directions, which chatlogs_private (MyISAM, one
 * index per column) could only answer by scanning, and the adoption ran it inside the send transaction. Each
 * direction is now read through idx_chatlogs_private_pair; this checks on a migrated MariaDB that the results
 * are the same lines: only the pair, newest first when paging, the newest LEGACY_ADOPT_LIMIT oldest first when
 * adopted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MessengerLegacyPairQueryIT {

    private static final int ALICE = 1_910_000_001;
    private static final int BOB = 1_910_000_002;
    private static final int CAROL = 1_910_000_003;
    private static final int PAIR_LINES = 600;

    private HikariDataSource dataSource;
    private final List<Integer> pairIds = new ArrayList<>();

    private static void requireDocker() {
        try {
            TestDatabase.sharedDataSource();
        } catch (Throwable error) {
            if ("true".equalsIgnoreCase(System.getenv("CI"))) {
                throw new AssertionError("Docker/Testcontainers is required in CI", error);
            }
            assumeTrue(false, "Docker/Testcontainers is unavailable: " + error.getMessage());
        }
    }

    @BeforeAll
    void migrateAndLoadTheChatlogs() throws Exception {
        requireDocker();
        this.dataSource = TestDatabase.freshDatabase("messenger_legacy_pair");
        MigrationRunner.migrate(this.dataSource);

        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO chatlogs_private (user_from_id, user_to_id, message, timestamp) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            for (int line = 0; line < PAIR_LINES; line++) {
                boolean fromAlice = line % 2 == 0;
                insertLine(insert, fromAlice ? ALICE : BOB, fromAlice ? BOB : ALICE, "pair " + line, true);
                // Other conversations of the same users, interleaved, never belong to the pair.
                if (line % 10 == 0) {
                    insertLine(insert, ALICE, CAROL, "carol " + line, false);
                    insertLine(insert, CAROL, BOB, "carol " + line, false);
                }
            }
            // An empty line is read as history but never adopted.
            insertLine(insert, BOB, ALICE, "", true);
        }
    }

    private void insertLine(PreparedStatement insert, int from, int to, String message, boolean pair) throws Exception {
        insert.setInt(1, from);
        insert.setInt(2, to);
        insert.setString(3, message);
        insert.setInt(4, 1_700_000_000 + this.pairIds.size());
        insert.executeUpdate();
        try (ResultSet keys = insert.getGeneratedKeys()) {
            keys.next();
            if (pair) {
                this.pairIds.add(keys.getInt(1));
            }
        }
    }

    @AfterAll
    void closeTheDatabase() {
        if (this.dataSource != null) {
            this.dataSource.close();
        }
    }

    private long conversationWithBob(JdbcMessengerHistoryRepository repository) {
        return repository.listConversations(ALICE).stream()
                .filter(summary -> summary.participantId() == BOB)
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static List<Long> ids(List<MessengerStoredMessage> messages) {
        return messages.stream().map(MessengerStoredMessage::id).toList();
    }

    private List<Long> newestPairIds(int skip, int count) {
        List<Long> newest = new ArrayList<>();
        for (int index = this.pairIds.size() - 1 - skip; index >= 0 && newest.size() < count; index--) {
            newest.add((long) this.pairIds.get(index));
        }
        return newest;
    }

    @Test
    @Order(1)
    void thePairIndexExists() throws Exception {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM information_schema.STATISTICS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'chatlogs_private' "
                                + "AND INDEX_NAME = 'idx_chatlogs_private_pair'");
                ResultSet result = statement.executeQuery()) {
            assertTrue(result.next());
            assertEquals("user_from_id,user_to_id,id", result.getString(1));
        }
    }

    @Test
    @Order(2)
    void theLegacyHistoryPagesThroughBothDirectionsOfThePairOnly() {
        JdbcMessengerHistoryRepository repository = new JdbcMessengerHistoryRepository(this.dataSource);
        long conversation = this.conversationWithBob(repository);

        List<MessengerStoredMessage> firstPage = repository.loadHistory(conversation, ALICE, 0, 20);
        assertEquals(this.newestPairIds(0, 21), ids(firstPage), "the newest lines of both directions, plus one");

        long before = firstPage.get(19).id();
        List<MessengerStoredMessage> secondPage = repository.loadHistory(conversation, ALICE, before, 20);
        assertEquals(this.newestPairIds(20, 21), ids(secondPage), "the next page starts below the last line read");
    }

    /** Runs last: once the conversation has a stored message, its history is read from messenger_messages. */
    @Test
    @Order(3)
    void theFirstStoredMessageAdoptsTheNewestNonEmptyLinesOldestFirst() throws Exception {
        JdbcMessengerHistoryRepository repository = new JdbcMessengerHistoryRepository(this.dataSource);
        this.conversationWithBob(repository);

        MessengerStoredMessage stored = repository.storeDirectMessage(ALICE, BOB, 0, "hello", null);

        List<String> adopted = new ArrayList<>();
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT message FROM messenger_messages WHERE conversation_id = ? ORDER BY id")) {
            statement.setLong(1, stored.conversationId());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) adopted.add(result.getString(1));
            }
        }

        int limit = JdbcMessengerHistoryRepository.LEGACY_ADOPT_LIMIT;
        assertEquals(limit + 1, adopted.size(), "the newest lines up to the limit, then the new message");
        assertEquals("pair " + (PAIR_LINES - limit), adopted.get(0), "oldest adopted line first");
        assertEquals("pair " + (PAIR_LINES - 1), adopted.get(limit - 1), "newest adopted line last");
        assertEquals("hello", adopted.get(limit));
        assertTrue(adopted.stream().noneMatch(line -> line.startsWith("carol")), "no line of another pair");
    }
}
