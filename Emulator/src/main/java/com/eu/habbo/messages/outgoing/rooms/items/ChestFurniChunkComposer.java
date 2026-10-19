package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.items.interactions.wired.chest.ChestFurniStoredItem;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * Furni-chest storage chunk (official client-6 {@code Sebahew} / header 2323 wire shape).
 * We use header {@link Outgoing#ChestFurniChunkComposer} (9322) because 2323 is taken in Nitro.
 */
public class ChestFurniChunkComposer extends MessageComposer {
    public static final int CHUNK_SIZE = 100;

    private final int chestId;
    private final int totalFragments;
    private final int fragmentNo;
    private final List<ChestFurniStoredItem> chunk;

    public ChestFurniChunkComposer(int chestId, int totalFragments, int fragmentNo, List<ChestFurniStoredItem> chunk) {
        this.chestId = chestId;
        this.totalFragments = totalFragments;
        this.fragmentNo = fragmentNo;
        this.chunk = chunk;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ChestFurniChunkComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.chestId));
        this.response.appendInt(this.totalFragments);
        this.response.appendInt(this.fragmentNo);
        this.response.appendInt(this.chunk.size());
        for (ChestFurniStoredItem item : this.chunk) {
            item.appendToMessage(this.response);
        }
        return this.response;
    }
}
