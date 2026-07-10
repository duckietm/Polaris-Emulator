package com.eu.habbo.habbohotel.messenger.history;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class JdbcMessengerHistoryRepository implements MessengerHistoryRepository {
    private static final int CLEANUP_BATCH_SIZE = 1000;

    private final DataSource dataSource;

    public JdbcMessengerHistoryRepository(DataSource dataSource) {
        if (dataSource == null) throw new IllegalArgumentException("dataSource is required");
        this.dataSource = dataSource;
    }

    @Override
    public List<MessengerConversationSummary> listConversations(int userId) {
        String sql = """
                SELECT c.id, c.type,
                       CASE WHEN c.type = 'direct' THEN COALESCE(MAX(CASE WHEN peer.user_id <> ? THEN peer.user_id END), 0) ELSE 0 END AS participant_id,
                       COALESCE(c.name, MAX(CASE WHEN peer.user_id <> ? THEN users.username END), '') AS display_name,
                       COALESCE(MAX(m.id), 0) AS last_message_id,
                       COUNT(DISTINCT CASE WHEN m.id > COALESCE(member.last_read_message_id, 0) AND m.sender_id <> ? THEN m.id END) AS unread_count,
                       UNIX_TIMESTAMP(c.updated_at) AS updated_at
                FROM messenger_members member
                JOIN messenger_conversations c ON c.id = member.conversation_id
                JOIN messenger_members peer ON peer.conversation_id = c.id AND peer.left_at IS NULL
                LEFT JOIN users ON users.id = peer.user_id
                LEFT JOIN messenger_messages m ON m.conversation_id = c.id
                WHERE member.user_id = ? AND member.left_at IS NULL
                GROUP BY c.id, c.type, c.name, member.last_read_message_id, c.updated_at
                ORDER BY c.updated_at DESC
                """;
        List<MessengerConversationSummary> summaries = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setInt(2, userId);
            statement.setInt(3, userId);
            statement.setInt(4, userId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    summaries.add(new MessengerConversationSummary(
                            result.getLong("id"),
                            ConversationType.valueOf(result.getString("type").toUpperCase()),
                            result.getInt("participant_id"),
                            result.getString("display_name"),
                            result.getLong("last_message_id"),
                            result.getInt("unread_count"),
                            result.getLong("updated_at")
                    ));
                }
            }
            return summaries;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list messenger conversations", exception);
        }
    }

    @Override
    public boolean isActiveMember(long conversationId, int userId) {
        String sql = "SELECT 1 FROM messenger_members WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL LIMIT 1";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            statement.setInt(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to validate messenger membership", exception);
        }
    }

    @Override
    public List<Integer> listActiveMemberIds(long conversationId) {
        String sql = "SELECT user_id FROM messenger_members WHERE conversation_id = ? AND left_at IS NULL";
        List<Integer> userIds = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, conversationId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) userIds.add(result.getInt("user_id"));
            }
            return userIds;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list messenger members", exception);
        }
    }

    @Override
    public List<MessengerStoredMessage> loadHistory(long conversationId, int userId, long beforeMessageId, int limit) {
        String sql = """
                SELECT m.id, m.conversation_id, m.sender_id, m.type, m.message, m.metadata,
                       UNIX_TIMESTAMP(m.created_at) AS created_at
                FROM messenger_messages m
                JOIN messenger_members member
                  ON member.conversation_id = m.conversation_id AND member.user_id = ?
                WHERE m.conversation_id = ?
                  AND (? = 0 OR m.id < ?)
                  AND (member.joined_message_id IS NULL OR m.id >= member.joined_message_id)
                  AND (member.left_message_id IS NULL OR m.id <= member.left_message_id)
                ORDER BY m.id DESC
                LIMIT ?
                """;
        List<MessengerStoredMessage> messages = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setLong(2, conversationId);
            statement.setLong(3, beforeMessageId);
            statement.setLong(4, beforeMessageId);
            statement.setInt(5, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new MessengerStoredMessage(
                            result.getLong("id"),
                            result.getLong("conversation_id"),
                            result.getInt("sender_id"),
                            result.getInt("type"),
                            result.getString("message"),
                            result.getString("metadata"),
                            result.getLong("created_at")
                    ));
                }
            }
            return messages;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load messenger history", exception);
        }
    }

    @Override
    public MessengerStoredMessage storeDirectMessage(int senderId, int recipientId, int type, String message, String metadata) {
        String conversationSql = "INSERT INTO messenger_conversations (type, direct_key) VALUES ('direct', ?) ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id), updated_at = CURRENT_TIMESTAMP";
        String memberSql = "INSERT INTO messenger_members (conversation_id, user_id) VALUES (?, ?) ON DUPLICATE KEY UPDATE left_at = NULL, left_message_id = NULL";
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long conversationId;
                try (PreparedStatement statement = connection.prepareStatement(conversationSql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, MessengerHistoryService.directKey(senderId, recipientId));
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("missing conversation id");
                        conversationId = keys.getLong(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(memberSql)) {
                    statement.setLong(1, conversationId);
                    statement.setInt(2, senderId);
                    statement.addBatch();
                    statement.setLong(1, conversationId);
                    statement.setInt(2, recipientId);
                    statement.addBatch();
                    statement.executeBatch();
                }
                MessengerStoredMessage stored = insertMessage(connection, conversationId, senderId, type, message, metadata);
                connection.commit();
                return stored;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to store direct messenger message", exception);
        }
    }

    @Override
    public MessengerStoredMessage storeConversationMessage(long conversationId, int senderId, int type, String message, String metadata) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                MessengerStoredMessage stored = insertMessage(connection, conversationId, senderId, type, message, metadata);
                connection.commit();
                return stored;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to store messenger message", exception);
        }
    }

    private MessengerStoredMessage insertMessage(Connection connection, long conversationId, int senderId, int type, String message, String metadata) throws SQLException {
        String sql = "INSERT INTO messenger_messages (conversation_id, sender_id, type, message, metadata) VALUES (?, ?, ?, ?, ?)";
        long messageId;
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, conversationId);
            statement.setInt(2, senderId);
            statement.setInt(3, type);
            statement.setString(4, message);
            statement.setString(5, metadata);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("missing message id");
                messageId = keys.getLong(1);
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("UPDATE messenger_conversations SET updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            statement.setLong(1, conversationId);
            statement.executeUpdate();
        }
        return new MessengerStoredMessage(messageId, conversationId, senderId, type, message, metadata, System.currentTimeMillis() / 1000L);
    }

    @Override
    public boolean markRead(long conversationId, int userId, long messageId) {
        String sql = "UPDATE messenger_members SET last_read_message_id = GREATEST(COALESCE(last_read_message_id, 0), ?) WHERE conversation_id = ? AND user_id = ? AND left_at IS NULL";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, messageId);
            statement.setLong(2, conversationId);
            statement.setInt(3, userId);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to update messenger read cursor", exception);
        }
    }

    @Override
    public void cleanupRetention(int days, int maxMessagesPerConversation) {
        String deleteExpired = "DELETE FROM messenger_messages WHERE created_at < UTC_TIMESTAMP() - INTERVAL ? DAY LIMIT ?";
        String deleteOverflow = """
                DELETE messages FROM messenger_messages messages
                JOIN (
                    SELECT overflow_rows.id
                    FROM (
                        SELECT older.id
                        FROM messenger_messages older
                        JOIN messenger_messages newer
                          ON newer.conversation_id = older.conversation_id
                         AND newer.id > older.id
                        GROUP BY older.id
                        HAVING COUNT(newer.id) >= ?
                        LIMIT ?
                    ) overflow_rows
                ) doomed ON doomed.id = messages.id
                """;
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(deleteExpired)) {
                statement.setInt(1, days);
                statement.setInt(2, CLEANUP_BATCH_SIZE);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement(deleteOverflow)) {
                statement.setInt(1, maxMessagesPerConversation);
                statement.setInt(2, CLEANUP_BATCH_SIZE);
                statement.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clean messenger history", exception);
        }
    }
}
