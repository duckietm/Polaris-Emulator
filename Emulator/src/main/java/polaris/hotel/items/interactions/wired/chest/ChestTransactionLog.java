package polaris.hotel.items.interactions.wired.chest;

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
import polaris.Emulator;
import polaris.WiredCompatibilityDiagnostics;
import polaris.WiredPlatform;
import polaris.hotel.users.User;

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

    /** AIR's transaction methods ({@code wired_transactions.type.N}). */
    public static final int METHOD_MANUAL = 0;

    public static final int METHOD_WIRED = 1;
    public static final int METHOD_CONTRACT_PAYMENT = 2;
    public static final int METHOD_CONTRACT_REWARD = 3;
    public static final int METHOD_CONTRACT_TRADE = 4;

    /** AIR's list types: one chest's log, or the room's. */
    public static final int LIST_CHEST = 0;

    public static final int LIST_ROOM = 1;

    /** Furni lines sent with one transaction's details; past it the details are marked incomplete. */
    static final int MAX_DETAIL_LINES = 200;

    /**
     * One transaction as AIR lists it: everything one action moved, over however many chests it touched. Our table
     * keeps a row per chest, tied together by {@code transaction_key}; {@code id} is the first row's id.
     */
    public record Transaction(
            int id,
            int roomId,
            int method,
            String definitionInfo,
            int userId,
            String userName,
            int timestamp,
            int chestCount,
            int withdrawnFurni,
            int depositedFurni,
            int withdrawnCoins,
            int depositedCoins) {}

    /** A page of transactions, as AIR's list message: which list, its id, the total, the page and its size. */
    public record TransactionPage(int listType, int listId, int total, int page, int amount, List<Transaction> rows) {}

    /**
     * One aggregated furni line inside a transaction's detail. {@code spriteId} is negative for a wall
     * item: floor and wall furni have separate sprite id ranges, so the sign is what tells the detail
     * window which icon to draw.
     */
    public record DetailItem(int spriteId, int quantity) {}

    /** One transaction with its chests and the furni it took out and put in. */
    public record TransactionDetails(
            Transaction transaction,
            List<Integer> chestIds,
            List<DetailItem> withdrawn,
            List<DetailItem> deposited,
            boolean incomplete) {}

    /**
     * Everything logged on this thread until it is closed belongs to one transaction of the given method, as one
     * contract or one wired box moving things in and out of several chests is one line in AIR's log. Scopes do not
     * nest: an inner one joins the transaction already running.
     */
    public static final class Scope implements AutoCloseable {
        private final boolean owner;

        private Scope(boolean owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (this.owner) CURRENT.remove();
        }
    }

    private record Running(long key, int method, String definitionInfo) {}

    private static final ThreadLocal<Running> CURRENT = new ThreadLocal<>();
    // Above every row id, so a key never reads as the id of a row logged on its own.
    private static final java.util.concurrent.atomic.AtomicLong KEYS =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis() * 1000L);

    public static Scope begin(int method, String definitionInfo) {
        if (CURRENT.get() != null) return new Scope(false);

        CURRENT.set(new Running(KEYS.incrementAndGet(), method, clampInfo(definitionInfo)));
        return new Scope(true);
    }

    /** A wired box's extra info, as AIR describes it: the box's furni id and the amount it hands out. */
    public static String wiredInfo(int boxId, int amount, boolean everything) {
        return boxId + ", " + (everything ? "all" : String.valueOf(amount));
    }

    /** A contract's extra info, as AIR describes it: the contract id and the multiplier used. */
    public static String contractInfo(int contractId, int times) {
        return contractId + ", x" + Math.max(1, times);
    }

    static String clampInfo(String info) {
        if (info == null) return "";
        return info.length() > 255 ? info.substring(0, 255) : info;
    }

    /** The method of a row logged outside a scope: a player at the chest window, or wired. */
    static int methodOf(int storedMethod, int source) {
        if (storedMethod >= METHOD_MANUAL) return storedMethod;
        return source == SOURCE_WIRED ? METHOD_WIRED : METHOD_MANUAL;
    }

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
            User user,
            int currencyType,
            int withdrawn,
            int deposited,
            List<ChestFurniStoredItem> items) {
        polaris.threading.ThreadPooling threading = WiredPlatform.threading();
        if (roomId <= 0 || chestId <= 0 || threading == null) return;

        int userId = user == null ? 0 : user.getUserInfo().getId();
        String userName = user == null ? "" : user.getUserInfo().getUsername();
        String details = encodeDetails(items);
        int timestamp = Emulator.getIntUnixTimestamp();
        Running running = CURRENT.get();
        long key = running == null ? 0L : running.key();
        int method = running == null ? -1 : running.method();
        String info = running == null ? "" : running.definitionInfo();

        threading.runPersistence(() -> insert(
                key,
                method,
                info,
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
            long key,
            int method,
            String info,
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
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO wired_chest_transactions (room_id, chest_id, chest_kind, transaction_type,"
                                + " source, user_id, user_name, currency_type, withdrawn, deposited, details,"
                                + " timestamp, transaction_key, method, definition_info)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
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
            statement.setLong(13, key);
            statement.setInt(14, method);
            statement.setString(15, info);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception writing a wired chest transaction", e);
        }
    }

    /** A transaction's rows share a key; a row logged on its own is its own transaction. */
    private static final String GROUP = "IF(transaction_key = 0, -id, transaction_key)";

    private static final String TRANSACTION_COLUMNS = "MIN(id) AS tx_id, MAX(method) AS method, MAX(source) AS source,"
            + " MAX(definition_info) AS definition_info, MAX(user_id) AS user_id, MAX(user_name) AS user_name,"
            + " MAX(timestamp) AS timestamp, COUNT(DISTINCT chest_id) AS chest_count,"
            + " SUM(IF(chest_kind = 1, withdrawn, 0)) AS withdrawn_furni,"
            + " SUM(IF(chest_kind = 1, deposited, 0)) AS deposited_furni,"
            + " SUM(IF(chest_kind = 0, withdrawn, 0)) AS withdrawn_coins,"
            + " SUM(IF(chest_kind = 0, deposited, 0)) AS deposited_coins";

    /**
     * One page of the log, newest first, optionally narrowed to the transactions that touched one chest. Page
     * numbers are 1-based; a page past the end comes back as the last one.
     *
     * @param chestId the chest to read, or zero for the whole room
     */
    public static TransactionPage transactions(int roomId, int filter, int amount, int page, int chestId) {
        int size = Math.max(1, Math.min(amount, MAX_PAGE_SIZE));
        int requested = Math.max(1, page);
        int normalizedFilter = normalizeFilter(filter);

        if (requested == 1) prune(roomId);

        String where = "room_id = ?" + filterClause(normalizedFilter) + transactionChestClause(chestId);
        int total = countTransactions(roomId, where, chestId);
        int pageCount = Math.max(1, (int) Math.ceil(total / (double) size));
        int current = Math.min(requested, pageCount);
        List<Transaction> rows = new ArrayList<>();

        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("SELECT " + TRANSACTION_COLUMNS
                        + " FROM wired_chest_transactions WHERE " + where + " GROUP BY " + GROUP
                        + " ORDER BY tx_id DESC LIMIT ? OFFSET ?")) {
            int index = bindWhere(statement, roomId, chestId);
            statement.setInt(index++, size);
            statement.setInt(index, (current - 1) * size);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) rows.add(readTransaction(set, roomId));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception reading wired chest transactions", e);
        }

        return new TransactionPage(
                chestId > 0 ? LIST_CHEST : LIST_ROOM, chestId > 0 ? chestId : roomId, total, current, size, rows);
    }

    /**
     * Read one transaction with its chests and furni. Returns {@code null} when the id does not belong to
     * this room, so a crafted id cannot read another room's log.
     */
    public static TransactionDetails transactionDetails(int roomId, int transactionId) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, chest_id, chest_kind, transaction_type, source, user_id, user_name, withdrawn,"
                                + " deposited, details, timestamp, method, definition_info"
                                + " FROM wired_chest_transactions WHERE room_id = ? AND " + GROUP + " = (SELECT "
                                + GROUP + " FROM wired_chest_transactions WHERE id = ? AND room_id = ?)"
                                + " ORDER BY id")) {
            statement.setInt(1, roomId);
            statement.setInt(2, transactionId);
            statement.setInt(3, roomId);

            List<StoredRow> stored = new ArrayList<>();
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    stored.add(new StoredRow(
                            set.getInt("id"),
                            set.getInt("chest_id"),
                            set.getInt("chest_kind"),
                            set.getInt("transaction_type"),
                            set.getInt("source"),
                            set.getInt("user_id"),
                            set.getString("user_name") == null ? "" : set.getString("user_name"),
                            set.getInt("withdrawn"),
                            set.getInt("deposited"),
                            set.getString("details"),
                            set.getInt("timestamp"),
                            set.getInt("method"),
                            set.getString("definition_info") == null ? "" : set.getString("definition_info")));
                }
            }
            return stored.isEmpty() ? null : combine(roomId, stored);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception reading a wired chest transaction detail", e);
        }
        return null;
    }

    /** One stored row: what one chest gave or took in a transaction. */
    record StoredRow(
            int id,
            int chestId,
            int chestKind,
            int type,
            int source,
            int userId,
            String userName,
            int withdrawn,
            int deposited,
            String details,
            int timestamp,
            int method,
            String definitionInfo) {}

    /** The rows of one transaction as AIR's details: its totals, its chests and its furni per direction. */
    static TransactionDetails combine(int roomId, List<StoredRow> stored) {
        StoredRow first = stored.get(0);
        java.util.LinkedHashSet<Integer> chests = new java.util.LinkedHashSet<>();
        Map<Integer, Integer> out = new LinkedHashMap<>();
        Map<Integer, Integer> in = new LinkedHashMap<>();
        int withdrawnFurni = 0;
        int depositedFurni = 0;
        int withdrawnCoins = 0;
        int depositedCoins = 0;
        int method = -1;
        int source = SOURCE_USER;
        int timestamp = 0;
        String info = "";
        int listedOut = 0;
        int listedIn = 0;

        for (StoredRow row : stored) {
            chests.add(row.chestId());
            method = Math.max(method, row.method());
            source = Math.max(source, row.source());
            timestamp = Math.max(timestamp, row.timestamp());
            if (info.isEmpty()) info = row.definitionInfo();

            if (row.chestKind() == ChestStorage.KIND_FURNI) {
                withdrawnFurni += row.withdrawn();
                depositedFurni += row.deposited();
                for (DetailItem item : decodeDetails(row.details())) {
                    if (row.type() == TYPE_WITHDRAW) {
                        out.merge(item.spriteId(), item.quantity(), Integer::sum);
                        listedOut += item.quantity();
                    } else {
                        in.merge(item.spriteId(), item.quantity(), Integer::sum);
                        listedIn += item.quantity();
                    }
                }
            } else {
                withdrawnCoins += row.withdrawn();
                depositedCoins += row.deposited();
            }
        }

        // Furni the counts name and the lines do not: a row written without its furni, or more kinds than we send.
        boolean incomplete = listedOut < withdrawnFurni
                || listedIn < depositedFurni
                || out.size() > MAX_DETAIL_LINES
                || in.size() > MAX_DETAIL_LINES;

        return new TransactionDetails(
                new Transaction(
                        first.id(),
                        roomId,
                        methodOf(method, source),
                        info,
                        first.userId(),
                        first.userName(),
                        timestamp,
                        chests.size(),
                        withdrawnFurni,
                        depositedFurni,
                        withdrawnCoins,
                        depositedCoins),
                new ArrayList<>(chests),
                lines(out),
                lines(in),
                incomplete);
    }

    private static List<DetailItem> lines(Map<Integer, Integer> byType) {
        List<DetailItem> lines = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : byType.entrySet()) {
            if (lines.size() >= MAX_DETAIL_LINES) break;
            lines.add(new DetailItem(entry.getKey(), entry.getValue()));
        }
        return lines;
    }

    // ---- The per-chest rows as they were listed before the log followed AIR's transactions. Kept for plugins
    // compiled against them; the hotel's own windows read {@link #transactions} and {@link #transactionDetails}.

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

    /** A single transaction with the furni it moved. Empty item list for currency rows. */
    public record Details(Row row, List<DetailItem> items) {}

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

        try (Connection connection = connection();
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
        try (Connection connection = connection();
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

    private static int count(int roomId, String where, int chestId) {
        try (Connection connection = connection();
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

    /** Bound as a parameter rather than inlined: a chest id arrives from the client. */
    private static String chestClause(int chestId) {
        return chestId > 0 ? " AND chest_id = ?" : "";
    }

    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** Drop rows past the retention window for one room. */
    public static void prune(int roomId) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM wired_chest_transactions WHERE room_id = ? AND timestamp < ?")) {
            statement.setInt(1, roomId);
            statement.setInt(2, Emulator.getIntUnixTimestamp() - RETENTION_SECONDS);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception pruning wired chest transactions", e);
        }
    }

    private static int countTransactions(int roomId, String where, int chestId) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(DISTINCT " + GROUP + ") FROM wired_chest_transactions WHERE " + where)) {
            bindWhere(statement, roomId, chestId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception counting wired chest transactions", e);
        }
        return 0;
    }

    /** Binds what {@code where} asks for and answers the next free parameter index. */
    private static int bindWhere(PreparedStatement statement, int roomId, int chestId) throws SQLException {
        int index = 1;
        statement.setInt(index++, roomId);
        if (chestId > 0) {
            statement.setInt(index++, roomId);
            statement.setInt(index++, chestId);
        }
        return index;
    }

    private static Transaction readTransaction(ResultSet set, int roomId) throws SQLException {
        return new Transaction(
                set.getInt("tx_id"),
                roomId,
                methodOf(set.getInt("method"), set.getInt("source")),
                set.getString("definition_info") == null ? "" : set.getString("definition_info"),
                set.getInt("user_id"),
                set.getString("user_name") == null ? "" : set.getString("user_name"),
                set.getInt("timestamp"),
                set.getInt("chest_count"),
                set.getInt("withdrawn_furni"),
                set.getInt("deposited_furni"),
                set.getInt("withdrawn_coins"),
                set.getInt("deposited_coins"));
    }

    /** Only the filter values the client may send; anything else reads as "everything". */
    public static int normalizeFilter(int filter) {
        return (filter == FILTER_CURRENCY || filter == FILTER_FURNI) ? filter : FILTER_ALL;
    }

    /**
     * Every transaction that touched the chest, whole: its rows in other chests count too. Bound as parameters
     * rather than inlined: a chest id arrives from the client.
     */
    private static String transactionChestClause(int chestId) {
        return chestId > 0
                ? " AND " + GROUP + " IN (SELECT IF(c.transaction_key = 0, -c.id, c.transaction_key)"
                        + " FROM wired_chest_transactions c WHERE c.room_id = ? AND c.chest_id = ?)"
                : "";
    }

    private static String filterClause(int filter) {
        return switch (filter) {
            case FILTER_CURRENCY -> " AND chest_kind = " + ChestStorage.KIND_CURRENCY;
            case FILTER_FURNI -> " AND chest_kind = " + ChestStorage.KIND_FURNI;
            default -> "";
        };
    }

    /**
     * Aggregate the moved furni into {@code spriteId:quantity} pairs (a wall item's sprite id negated,
     * see {@link DetailItem}) and store them as a compact {@code "id:count,id:count"} string. Deliberately not JSON: the payload is read back only here,
     * and a flat pair list stays small for a hundred-item withdraw.
     */
    static String encodeDetails(List<ChestFurniStoredItem> items) {
        if (items == null || items.isEmpty()) return null;

        Map<Integer, Integer> byType = new LinkedHashMap<>();
        for (ChestFurniStoredItem item : items) {
            if (item != null) byType.merge(item.wallItem ? -item.wireTypeId() : item.wireTypeId(), 1, Integer::sum);
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
                if (spriteId != 0 && quantity > 0) items.add(new DetailItem(spriteId, quantity));
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
