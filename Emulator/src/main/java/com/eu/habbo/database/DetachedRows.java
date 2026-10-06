package com.eu.habbo.database;

import com.eu.habbo.WiredPlatform;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.rowset.CachedRowSet;
import javax.sql.rowset.RowSetProvider;

/**
 * Reads rows into memory and gives the connection back before they are mapped. For mappers that run
 * queries of their own (a user's row builds the user, which loads currencies, inventory, messenger):
 * mapping inside the connection held one pooled connection per caller while asking for another, so
 * enough callers at once (logins after a restart) starved the pool for good.
 */
public final class DetachedRows {
    @FunctionalInterface
    public interface Binder {
        void bind(PreparedStatement statement) throws SQLException;
    }

    private DetachedRows() {}

    /** The rows of {@code sql}, before the first one like a fresh ResultSet; close it when done. */
    public static CachedRowSet read(String sql, Binder binder) throws SQLException {
        Database database = WiredPlatform.database();
        if (database == null) {
            throw new SQLException("database not available");
        }
        try (Connection connection = database.getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            binder.bind(statement);
            try (ResultSet set = statement.executeQuery()) {
                return copy(set);
            }
        }
    }

    /** A copy of the remaining rows of {@code set}. */
    static CachedRowSet copy(ResultSet set) throws SQLException {
        CachedRowSet rows = RowSetProvider.newFactory().createCachedRowSet();
        rows.populate(set);
        return rows;
    }
}
