package com.eu.habbo.habbohotel.soundboard;

import com.google.gson.Gson;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

public class SoundboardCatalogRepository {
    private static final Gson GSON = new Gson();
    private static final String SELECT_FIELDS = "id, name, url, enabled, sort_order, min_rank";

    private final DataSource dataSource;

    public SoundboardCatalogRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public List<SoundboardSound> loadAll() throws SQLException {
        try (Connection connection = this.dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT " + SELECT_FIELDS + " FROM soundboard_sounds ORDER BY sort_order ASC, id ASC");
                ResultSet resultSet = statement.executeQuery()) {
            List<SoundboardSound> sounds = new ArrayList<>();
            while (resultSet.next()) {
                sounds.add(new SoundboardSound(resultSet));
            }
            return List.copyOf(sounds);
        }
    }

    public SoundboardCatalogResult upsert(int staffUserId, SoundboardCatalogCommand command) {
        try (Connection connection = this.dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                SoundboardSound before = command.id() > 0 ? this.findForUpdate(connection, command.id()) : null;
                if (command.id() > 0 && before == null) {
                    connection.rollback();
                    return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.NOT_FOUND);
                }

                SoundboardSound after =
                        command.id() == 0 ? this.insert(connection, command) : this.update(connection, before, command);
                this.insertAudit(connection, staffUserId, actionFor(before, after), after.id, before, after);
                connection.commit();
                return SoundboardCatalogResult.success(after.id);
            } catch (SQLException exception) {
                connection.rollback();
                return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
        }
    }

    public SoundboardCatalogResult reorder(int staffUserId, List<Integer> orderedIds) {
        try (Connection connection = this.dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<SoundboardSound> before = this.loadAllForUpdate(connection);
                try (PreparedStatement update =
                        connection.prepareStatement("UPDATE soundboard_sounds SET sort_order = ? WHERE id = ?")) {
                    for (int index = 0; index < orderedIds.size(); index++) {
                        update.setInt(1, (index + 1) * 10);
                        update.setInt(2, orderedIds.get(index));
                        update.addBatch();
                    }
                    update.executeBatch();
                }
                this.insertAudit(connection, staffUserId, "reorder", null, before, orderedIds);
                connection.commit();
                return SoundboardCatalogResult.success(0);
            } catch (SQLException exception) {
                connection.rollback();
                return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            return SoundboardCatalogResult.failure(SoundboardCatalogResult.Code.PERSISTENCE_FAILURE);
        }
    }

    private SoundboardSound findForUpdate(Connection connection, int id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + SELECT_FIELDS + " FROM soundboard_sounds WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? new SoundboardSound(resultSet) : null;
            }
        }
    }

    private List<SoundboardSound> loadAllForUpdate(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT " + SELECT_FIELDS
                        + " FROM soundboard_sounds ORDER BY sort_order ASC, id ASC FOR UPDATE");
                ResultSet resultSet = statement.executeQuery()) {
            List<SoundboardSound> sounds = new ArrayList<>();
            while (resultSet.next()) {
                sounds.add(new SoundboardSound(resultSet));
            }
            return List.copyOf(sounds);
        }
    }

    private SoundboardSound insert(Connection connection, SoundboardCatalogCommand command) throws SQLException {
        int sortOrder = this.nextSortOrder(connection);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO soundboard_sounds (name, url, enabled, sort_order, min_rank) VALUES (?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, command.name());
            statement.setString(2, command.url());
            statement.setBoolean(3, command.enabled());
            statement.setInt(4, sortOrder);
            statement.setInt(5, command.minRank());
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("Soundboard insert returned no generated ID");
                }
                return new SoundboardSound(
                        keys.getInt(1), command.name(), command.url(), command.enabled(), sortOrder, command.minRank());
            }
        }
    }

    private SoundboardSound update(Connection connection, SoundboardSound before, SoundboardCatalogCommand command)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE soundboard_sounds SET name = ?, url = ?, enabled = ?, min_rank = ? WHERE id = ?")) {
            statement.setString(1, command.name());
            statement.setString(2, command.url());
            statement.setBoolean(3, command.enabled());
            statement.setInt(4, command.minRank());
            statement.setInt(5, command.id());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Soundboard update affected an unexpected number of rows");
            }
        }
        return new SoundboardSound(
                before.id, command.name(), command.url(), command.enabled(), before.sortOrder, command.minRank());
    }

    private int nextSortOrder(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(MAX(sort_order), 0) + 10 FROM soundboard_sounds FOR UPDATE");
                ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new SQLException("Unable to allocate Soundboard sort order");
            }
            return resultSet.getInt(1);
        }
    }

    private void insertAudit(
            Connection connection, int staffUserId, String action, Integer soundId, Object before, Object after)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO logs_soundboard "
                + "(staff_user_id, action, sound_id, before_snapshot, after_snapshot) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, staffUserId);
            statement.setString(2, action);
            if (soundId == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, soundId);
            }
            statement.setString(4, before == null ? null : GSON.toJson(before));
            statement.setString(5, after == null ? null : GSON.toJson(after));
            statement.executeUpdate();
        }
    }

    private static String actionFor(SoundboardSound before, SoundboardSound after) {
        if (before == null) {
            return "create";
        }
        if (before.enabled != after.enabled) {
            return after.enabled ? "enable" : "disable";
        }
        return "update";
    }
}
