package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * One page of a room's wired chest transactions, newest first, for the chests tab. Wire layout:
 *
 * <pre>
 *   int page, int pageCount, int totalRows, int filter,
 *   int rowCount,
 *   [ int transactionId, int chestId, int chestKind, int type, int source,
 *     int userId, string userName, int currencyType,
 *     int withdrawn, int deposited, bool hasDetails, int timestamp ]*
 * </pre>
 *
 * {@code type} is 0 deposit / 1 withdraw, {@code source} 0 player / 1 wired, {@code chestKind} 0
 * currency / 1 furni, and {@code timestamp} is unix seconds. {@code hasDetails} tells the client
 * whether asking for the per-transaction furni breakdown is worth a round trip.
 */
public class WiredChestRoomLogsComposer extends MessageComposer {
    private final ChestTransactionLog.Page page;

    public WiredChestRoomLogsComposer(ChestTransactionLog.Page page) {
        this.page = page;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredChestRoomLogsComposer);
        this.response.appendInt(this.page.page());
        this.response.appendInt(this.page.pageCount());
        this.response.appendInt(this.page.totalRows());
        this.response.appendInt(this.page.filter());

        this.response.appendInt(this.page.rows().size());
        for (ChestTransactionLog.Row row : this.page.rows()) {
            this.response.appendInt(row.id());
            this.response.appendInt(row.chestId());
            this.response.appendInt(row.chestKind());
            this.response.appendInt(row.type());
            this.response.appendInt(row.source());
            this.response.appendInt(row.userId());
            this.response.appendString(row.userName());
            this.response.appendInt(row.currencyType());
            this.response.appendInt(row.withdrawn());
            this.response.appendInt(row.deposited());
            this.response.appendBoolean(row.hasDetails());
            this.response.appendInt(row.timestamp());
        }

        return this.response;
    }
}
