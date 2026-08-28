package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestTransactionLog;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * One wired chest transaction with the furni it moved. Wire layout:
 *
 * <pre>
 *   int transactionId, int chestId, int chestKind, int type, int source,
 *   int userId, string userName, int currencyType,
 *   int withdrawn, int deposited, int timestamp,
 *   int itemCount, [ int spriteId, int quantity ]*
 * </pre>
 *
 * The item list is empty for a currency transaction. {@code spriteId} is the furnidata class id, the
 * same identity {@link ChestDataComposer} sends, so the client can render an icon without a lookup
 * table of its own.
 */
public class WiredChestTransactionDetailsComposer extends MessageComposer {
    private final ChestTransactionLog.Details details;

    public WiredChestTransactionDetailsComposer(ChestTransactionLog.Details details) {
        this.details = details;
    }

    @Override
    protected ServerMessage composeInternal() {
        ChestTransactionLog.Row row = this.details.row();

        this.response.init(Outgoing.WiredChestTransactionDetailsComposer);
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
        this.response.appendInt(row.timestamp());

        this.response.appendInt(this.details.items().size());
        for (ChestTransactionLog.DetailItem item : this.details.items()) {
            this.response.appendInt(item.spriteId());
            this.response.appendInt(item.quantity());
        }

        return this.response;
    }
}
