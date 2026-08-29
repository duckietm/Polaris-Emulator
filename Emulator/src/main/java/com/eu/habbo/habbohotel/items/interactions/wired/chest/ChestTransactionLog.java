package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Durable, room-scoped transaction log for wired storage chests ({@code wired_chest_transactions}).
 *
 * <p>Every chest already keeps a rolling in-furni log of its own last {@link ChestStorage#MAX_LOG}
 * rows, which is what the single-chest window reads. That log is not enough for the room-level chests
 * tab: it dies with the furni when it is picked up, cannot be paged, and carries no detail about which
 * furni moved. This class owns the room-scoped record instead.
 *
 * <p>Writes go through {@link #record} and are handed to the persistence pool, so a deposit or a
 * withdraw never waits on the database while holding the room. Reads are paged and happen on the
 * packet handler thread.
 */
public final class ChestTransactionLog {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChestTransactionLog.class);

    public static final int TYPE_DEPOSIT = 0;
    public static final int TYPE_WITHDRAW = 1;

    public static final int SOURCE_USER = 0;
    public static final int SOURCE_WIRED = 1;

    /** Row filters, matching the official chests tab: everything / only coins / only furni. */
    public static final int FILTER_ALL = 0;

    public static final int FILTER_CURRENCY = 1;
    public static final int FILTER_FURNI = 2;

    /** Upper bound on a single page, so a hostile client cannot ask for the whole table. */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Rows older than this are dropped the next time page 1 of that room is requested. Pruning on
     * read keeps the deposit/withdraw path free of extra statements; a room nobody ever opens the tab
     * for simply keeps its rows until someone does.
     */
    private static final int RETENTION_SECONDS = 30 * 24 * 60 * 60;

    private ChestTransactionLog() {}

    /** One logged transaction, without its furni detail. */
    public record Row(
            int id,
            int chestId,
            int chestKind,
            int type,
            int source,
            int userId,
            String userName,
            int currencyType,
            int withdrawn,
            int deposited,
            boolean hasDetails,
            int timestamp) {}

    /** A page of rows plus the paging state the client renders. */
    public record Page(int page, int pageCount, int totalRows, int filter, List<Row> rows) {}

    /** One aggregated furni line inside a transaction's detail. */
    public record DetailItem(int spriteId, int quantity) {}

    /** A single transaction with the furni it moved. Empty item list for currency rows. */
    public record Details(Row row, List<DetailItem> items) {}

    /**
     * Persist one transaction. Never throws into the caller: a failed log write must not roll back a
     * deposit the player already saw succeed.
     *
     * @param items the furni that moved, or {@code null} for a currency transaction
     */
    public static void record(
            int roomId,
            int chestId,
            int chestKind,
            int type,
            int source,
            Habbo habbo,
            int currencyType,
            int withdrawn,
            int deposited,
            List<ChestFurniStoredItem> items) {
        if (roomId <= 0 || chestId <= 0) return;

        int userId = habbo == null ? 0 : habbo.getHabboInfo().getId();
        String userName = habbo == null ? "" : habbo.getHabboInfo().getUsername();
        String details = encodeDetails(items);
        int timestamp = Emulator.getIntUnixTimestamp();

        Emulator.getThreading()
                .runPersistence(() -> insert(
                        roomId,
                        chestId,
                        chestKind,
                        type,
                        source,
                        userId,
                        userName == null ? "" : userName,
                        currencyType,
                        withdrawn,
                        deposited,
                        details,
                        timestamp));
    }

    private static void insert(
            int roomId,
            int chestId,
            int chestKind,
            int type,
            int source,
            int userId,
            String userName,
            int currencyType,
            int withdrawn,
            int deposited,
            String details,
            int timestamp) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO wired_chest_transactions (room_id, chest_id, chest_kind, transaction_type,"
                                + " source, user_id, user_name, currency_type, withdrawn, deposited, details,"
                                + " timestamp) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, roomId);
            statement.setInt(2, chestId);
            statement.setInt(3, chestKind);
            statement.setInt(4, type);
            statement.setInt(5, source);
            statement.setInt(6, userId);
            statement.setString(7, userName);
            statement.setInt(8, currencyType);
            statement.setInt(9, withdrawn);
            statement.setInt(10, deposited);
            if (details == null) {
                statement.setNull(11, java.sql.Types.VARCHAR);
            } else {
                statement.setString(11, details);
            }
            statement.setInt(12, timestamp);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception writing a wired chest transaction", e);
        }
    }

    /**
     * Read one page of a room's transactions, newest first. Page numbers are 1-based; a page past the
     * end comes back empty rather than as an error, so a stale client just renders nothing.
     */
    /** Every chest in the room. */
    public static Page page(int roomId, int filter, int amount, int page) {
        return page(roomId, filter, amount, page, 0);
    }

    /**
     * One page of the log, optionally narrowed to a single chest.
     *
     * @param chestId the chest to read, or zero for the whole room. A room with a dozen chests in
     *     it produces a log nobody can read; this is how you ask about the one you care about.
     */
    public static Page page(int roomId, int filter, int amount, int page, int chestId) {
        int size = Math.max(1, Math.min(amount, MAX_PAGE_SIZE));
        int requested = Math.max(1, page);
        int normalizedFilter = normalizeFilter(filter);

        if (requested == 1) prune(roomId);

        String where = "room_id = ?" + filterClause(normalizedFilter) + chestClause(chestId);
        int totalRows = count(roomId, where, chestId);
        int pageCount = Math.max(1, (int) Math.ceil(totalRows / (double) size));
        int current = Math.min(requested, pageCount);
        List<Row> rows = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT id, chest_id, chest_kind,"
                        + " transaction_type, source, user_id, user_name, currency_type, withdrawn, deposited,"
                        + " details IS NOT NULL AS has_details, timestamp FROM wired_chest_transactions WHERE "
                        + where + " ORDER BY id DESC LIMIT ? OFFSET ?")) {
            int index = 1;
            statement.setInt(index++, roomId);
            if (chestId > 0) statement.setInt(index++, chestId);
            statement.setInt(index++, size);
            statement.setInt(index, (current - 1) * size);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) rows.add(readRow(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception reading wired chest transactions", e);
        }

        return new Page(current, pageCount, totalRows, normalizedFilter, rows);
    }

    /**
     * Read one transaction with its furni detail. Returns {@code null} when the id does not belong to
     * this room, so a crafted id cannot read another room's log.
     */
    public static Details details(int roomId, int transactionId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT id, chest_id, chest_kind,"
                        + " transaction_type, source, user_id, user_name, currency_type, withdrawn, deposited,"
                        + " details IS NOT NULL AS has_details, timestamp, details FROM wired_chest_transactions"
                        + " WHERE id = ? AND room_id = ?")) {
            statement.setInt(1, transactionId);
            statement.setInt(2, roomId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return null;
                return new Details(readRow(set), decodeDetails(set.getString("details")));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception reading a wired chest transaction detail", e);
        }
        return null;
    }

    /** Drop rows past the retention window for one room. */
    public static void prune(int roomId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM wired_chest_transactions WHERE room_id = ? AND timestamp < ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, Emulator.getIntUnixTimestamp() - RETENTION_SECONDS);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception pruning wired chest transactions", e);
        }
    }

    private static int count(int roomId, String where, int chestId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM wired_chest_transactions WHERE " + where)) {
            statement.setInt(1, roomId);
            if (chestId > 0) statement.setInt(2, chestId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception counting wired chest transactions", e);
        }
        return 0;
    }

    private static Row readRow(ResultSet set) throws SQLException {
        return new Row(
                set.getInt("id"),
                set.getInt("chest_id"),
                set.getInt("chest_kind"),
                set.getInt("transaction_type"),
                set.getInt("source"),
                set.getInt("user_id"),
                set.getString("user_name") == null ? "" : set.getString("user_name"),
                set.getInt("currency_type"),
                set.getInt("withdrawn"),
                set.getInt("deposited"),
                set.getBoolean("has_details"),
                set.getInt("timestamp"));
    }

    /** Only the filter values the client may send; anything else reads as "everything". */
    public static int normalizeFilter(int filter) {
        return (filter == FILTER_CURRENCY || filter == FILTER_FURNI) ? filter : FILTER_ALL;
    }

    /** Bound as a parameter rather than inlined: a chest id arrives from the client. */
    private static String chestClause(int chestId) {
        return chestId > 0 ? " AND chest_id = ?" : "";
    }

    private static String filterClause(int filter) {
        return switch (filter) {
            case FILTER_CURRENCY -> " AND chest_kind = " + ChestStorage.KIND_CURRENCY;
            case FILTER_FURNI -> " AND chest_kind = " + ChestStorage.KIND_FURNI;
            default -> "";
        };
    }

    /**
     * Aggregate the moved furni into {@code spriteId:quantity} pairs and store them as a compact
     * {@code "id:count,id:count"} string. Deliberately not JSON: the payload is read back only here,
     * and a flat pair list stays small for a hundred-item withdraw.
     */
    static String encodeDetails(List<ChestFurniStoredItem> items) {
        if (items == null || items.isEmpty()) return null;

        Map<Integer, Integer> byType = new LinkedHashMap<>();
        for (ChestFurniStoredItem item : items) {
            if (item != null) byType.merge(item.wireTypeId(), 1, Integer::sum);
        }
        if (byType.isEmpty()) return null;

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : byType.entrySet()) {
            if (!builder.isEmpty()) builder.append(',');
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return builder.toString();
    }

    static List<DetailItem> decodeDetails(String encoded) {
        List<DetailItem> items = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return items;

        for (String pair : encoded.split(",")) {
            int separator = pair.indexOf(':');
            if (separator <= 0) continue;
            try {
                int spriteId = Integer.parseInt(pair.substring(0, separator).trim());
                int quantity = Integer.parseInt(pair.substring(separator + 1).trim());
                if (spriteId > 0 && quantity > 0) items.add(new DetailItem(spriteId, quantity));
            } catch (NumberFormatException malformedPair) {
                // A malformed pair drops out of the detail rather than failing the whole row, but it
                // is still recorded: a detail that silently loses lines is worse than a short one.
                WiredCompatibilityDiagnostics.record(
                        WiredCompatibilityDiagnostics.FailurePoint.CHEST_STORAGE_NUMERIC_FIELD, malformedPair);
            }
        }
        return items;
    }
}
