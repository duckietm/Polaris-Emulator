package polaris.messages.outgoing.rooms.items;

import java.util.List;
import polaris.hotel.items.interactions.wired.chest.ChestStorage;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Wired chest (Scrigno) transaction log, newest first. Wire layout:
 * {@code int itemId, int rowCount, [string type, int epochSeconds, string userName, int withdrawn, int deposited]*}.
 */
public class ChestLogComposer extends MessageComposer {
    private final InteractionWiredChest chest;

    public ChestLogComposer(InteractionWiredChest chest) {
        this.chest = chest;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestLogComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.chest.getId()));

        List<ChestStorage.LogEntry> log = this.chest.getContents().getLog();
        this.response.appendInt(log.size());

        for (ChestStorage.LogEntry e : log) {
            this.response.appendString(e.type == null ? "" : e.type);
            this.response.appendInt((int) (e.timestamp / 1000L));
            this.response.appendString(e.userName == null ? "" : e.userName);
            this.response.appendInt(e.withdrawn);
            this.response.appendInt(e.deposited);
        }

        return this.response;
    }
}
