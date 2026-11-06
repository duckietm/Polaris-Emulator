package polaris.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import polaris.Emulator;

public final class SqlQueries {

    private SqlQueries() {}

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface RowConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface ParameterBinder<P> {
        void bind(PreparedStatement ps, P value) throws SQLException;
    }

    public static class DataAccessException extends RuntimeException {
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException("query failed: " + sql, e);
        }
    }

    public static <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(mapper.map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("queryOne failed: " + sql, e);
        }
    }

    public static void forEach(String sql, RowConsumer consumer, Object... params) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    consumer.accept(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("forEach failed: " + sql, e);
        }
    }

    public static int update(String sql, Object... params) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            bindAll(ps, params);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("update failed: " + sql, e);
        }
    }

    public static <P> int[] batchUpdate(String sql, Collection<? extends P> items, ParameterBinder<P> binder) {
        try (Connection c = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (P item : items) {
                binder.bind(ps, item);
                ps.addBatch();
            }
            return ps.executeBatch();
        } catch (SQLException e) {
            throw new DataAccessException("batchUpdate failed: " + sql, e);
        }
    }

    private static void bindAll(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }
}
