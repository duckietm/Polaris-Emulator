package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

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
