package polaris.messages.outgoing.rooms.items;

import java.util.List;
import polaris.hotel.items.interactions.wired.chest.ChestFurniStoredItem;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * Furni-chest incremental update (official client-6 {@code Sola} / header 2738 wire shape).
 * We use header {@link Outgoing#ChestFurniDeltaComposer} (9323).
 */
public class ChestFurniDeltaComposer extends MessageComposer {
    private final int chestId;
    private final List<Integer> removedInventoryIds;
    private final List<ChestFurniStoredItem> added;

    public ChestFurniDeltaComposer(int chestId, List<Integer> removedInventoryIds, List<ChestFurniStoredItem> added) {
        this.chestId = chestId;
        this.removedInventoryIds = removedInventoryIds;
        this.added = added;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniDeltaComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.chestId));
        this.response.appendInt(this.removedInventoryIds.size());
        for (int id : this.removedInventoryIds) {
            this.response.appendInt(id);
        }
        this.response.appendInt(this.added.size());
        for (ChestFurniStoredItem item : this.added) {
            item.appendToMessage(this.response);
        }
        return this.response;
    }
}
