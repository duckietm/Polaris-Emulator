package polaris.messages.outgoing.rooms.items;

import java.util.List;
import polaris.hotel.items.interactions.wired.chest.ChestTransactionLog;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * One wired chest transaction with its chests and furni, as AIR's WiredTransactionDetails. Wire layout:
 *
 * <pre>
 *   the transaction (the row layout of {@link WiredChestRoomLogsComposer}),
 *   int chestCount, [ int chestId ]*,
 *   int withdrawnCount, [ bool wall, int typeId, string posterId, int quantity ]*,
 *   int depositedCount, [ bool wall, int typeId, string posterId, int quantity ]*,
 *   bool incomplete
 * </pre>
 *
 * {@code typeId} is the furnidata class id, the same identity {@link ChestDataComposer} sends. {@code incomplete}
 * says the lines do not name all the furni the counts do.
 */
public class WiredChestTransactionDetailsComposer extends MessageComposer {
    private final ChestTransactionLog.TransactionDetails details;

    public WiredChestTransactionDetailsComposer(ChestTransactionLog.TransactionDetails details) {
        this.details = details;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredChestTransactionDetailsComposer);
        WiredChestRoomLogsComposer.appendTransaction(this.response, this.details.transaction());
        this.response.appendInt(this.details.chestIds().size());
        for (Integer chestId : this.details.chestIds()) {
            this.response.appendInt(BuildersClubItemIds.toWire(chestId));
        }
        this.appendItems(this.details.withdrawn());
        this.appendItems(this.details.deposited());
        this.response.appendBoolean(this.details.incomplete());
        return this.response;
    }

    private void appendItems(List<ChestTransactionLog.DetailItem> items) {
        this.response.appendInt(items.size());
        for (ChestTransactionLog.DetailItem item : items) {
            // A wall item is stored with its type id negated.
            this.response.appendBoolean(item.spriteId() < 0);
            this.response.appendInt(Math.abs(item.spriteId()));
            this.response.appendString("");
            this.response.appendInt(item.quantity());
        }
    }
}
