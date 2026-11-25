package polaris.messages.outgoing.rooms.items;

import polaris.hotel.items.interactions.wired.chest.ChestTransactionLog;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * One page of wired chest transactions, newest first, as AIR's transaction list (WiredTransactionInfo rows; AIR's
 * 64-bit ids and times are ints here). Wire layout:
 *
 * <pre>
 *   int listType, int listId, int total, int page, int amount,
 *   int rowCount,
 *   [ int transactionId, int roomId, int method, string definitionInfo,
 *     int userId, string userName, int timestamp, string readableTimestamp,
 *     int chestCount, int withdrawnFurni, int depositedFurni, int withdrawnCoins, int depositedCoins ]*
 * </pre>
 *
 * {@code listType} is 0 for one chest's log ({@code listId} the chest) and 1 for the room's ({@code listId} the
 * room); {@code method} is AIR's {@code wired_transactions.type.N}; {@code timestamp} is unix seconds.
 */
public class WiredChestRoomLogsComposer extends MessageComposer {
    private final ChestTransactionLog.TransactionPage page;

    public WiredChestRoomLogsComposer(ChestTransactionLog.TransactionPage page) {
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredChestRoomLogsComposer);
        this.response.appendInt(this.page.listType());
        this.response.appendInt(listIdOnWire(this.page));
        this.response.appendInt(this.page.total());
        this.response.appendInt(this.page.page());
        this.response.appendInt(this.page.amount());
        this.response.appendInt(this.page.rows().size());
        for (ChestTransactionLog.Transaction row : this.page.rows()) {
            appendTransaction(this.response, row);
        }
        return this.response;
    }

    /** A chest's id as the client knows it; a room's as it is. */
    static int listIdOnWire(ChestTransactionLog.TransactionPage page) {
        return page.listType() == ChestTransactionLog.LIST_CHEST
                ? BuildersClubItemIds.toWire(page.listId())
                : page.listId();
    }

    static void appendTransaction(ServerMessage message, ChestTransactionLog.Transaction row) {
        message.appendInt(row.id());
        message.appendInt(row.roomId());
        message.appendInt(row.method());
        message.appendString(row.definitionInfo());
        message.appendInt(row.userId());
        message.appendString(row.userName());
        message.appendInt(row.timestamp());
        message.appendString(readable(row.timestamp()));
        message.appendInt(row.chestCount());
        message.appendInt(row.withdrawnFurni());
        message.appendInt(row.depositedFurni());
        message.appendInt(row.withdrawnCoins());
        message.appendInt(row.depositedCoins());
    }

    private static final java.time.format.DateTimeFormatter READABLE =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** The time as the log shows it, in the hotel's own zone. */
    static String readable(int unixSeconds) {
        return READABLE.format(java.time.LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(unixSeconds), java.time.ZoneId.systemDefault()));
    }
}
