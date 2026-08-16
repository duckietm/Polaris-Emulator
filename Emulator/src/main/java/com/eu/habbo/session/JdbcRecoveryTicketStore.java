package com.eu.habbo.session;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import javax.sql.DataSource;

final class JdbcRecoveryTicketStore implements RecoveryTicketStore {
    private static final int CHECKPOINT_BATCH_SIZE = 500;
    private final DataSource dataSource;

    JdbcRecoveryTicketStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void replace(int userId, byte[] tokenHash, Instant issuedAt) {
        String sql = "INSERT INTO session_recovery_tickets "
                + "(user_id, token_hash, issued_at, recoverable_until, consumed_at) VALUES (?, ?, ?, NULL, NULL) "
                + "ON DUPLICATE KEY UPDATE token_hash = VALUES(token_hash), issued_at = VALUES(issued_at), "
                + "recoverable_until = NULL, consumed_at = NULL";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            statement.setBytes(2, tokenHash);
            statement.setTimestamp(3, Timestamp.from(issuedAt));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new SessionRecoveryException("Could not issue a recovery ticket", exception);
        }
    }

    @Override
    public void activate(Iterable<Integer> userIds, Instant expiresAt) {
        List<Integer> ids = new ArrayList<>();
        userIds.forEach(ids::add);
        if (ids.isEmpty()) {
            return;
        }
        try (Connection connection = dataSource.getConnection()) {
            for (int offset = 0; offset < ids.size(); offset += CHECKPOINT_BATCH_SIZE) {
                List<Integer> batch = ids.subList(offset, Math.min(ids.size(), offset + CHECKPOINT_BATCH_SIZE));
                String placeholders = String.join(",", java.util.Collections.nCopies(batch.size(), "?"));
                String sql = "UPDATE session_recovery_tickets SET recoverable_until = ?, consumed_at = NULL "
                        + "WHERE user_id IN (" + placeholders + ")";
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setTimestamp(1, Timestamp.from(expiresAt));
                    for (int index = 0; index < batch.size(); index++) {
                        statement.setInt(index + 2, batch.get(index));
                    }
                    statement.executeUpdate();
                }
            }
        } catch (SQLException exception) {
            throw new SessionRecoveryException("Could not checkpoint recovery tickets", exception);
        }
    }

    @Override
    public OptionalInt consume(byte[] tokenHash, Instant now) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                int userId = selectForUpdate(connection, tokenHash, now);
                if (userId == 0) {
                    connection.rollback();
                    return OptionalInt.empty();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE session_recovery_tickets SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL")) {
                    statement.setTimestamp(1, Timestamp.from(now));
                    statement.setInt(2, userId);
                    if (statement.executeUpdate() != 1) {
                        connection.rollback();
                        return OptionalInt.empty();
                    }
                }
                connection.commit();
                return OptionalInt.of(userId);
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new SessionRecoveryException("Could not consume a recovery ticket", exception);
        }
    }

    private static int selectForUpdate(Connection connection, byte[] tokenHash, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT user_id FROM session_recovery_tickets "
                + "WHERE token_hash = ? AND consumed_at IS NULL AND recoverable_until >= ? FOR UPDATE")) {
            statement.setBytes(1, tokenHash);
            statement.setTimestamp(2, Timestamp.from(now));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }
}
