package com.eu.habbo.messages.outgoing.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.Mintable;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * AIR 15 relic list (official 340, taken here by the effects list: 9423): furni type id (the sprite id, as
 * the inventory sends it), start and end (unix seconds), region locked, stamp price, a flag, and the furni
 * kind (0 floor, 1 wall).
 */
public class CollectibleMintablesComposer extends MessageComposer {
    /** A relic with its furni's sprite id and whether the furni is a wall item. */
    public record Entry(Mintable mintable, int spriteId, boolean wallItem) {}

    private final List<Entry> mintables;

    public CollectibleMintablesComposer(List<Entry> mintables) {
        this.mintables = List.copyOf(mintables);
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CollectibleMintablesComposer);
        this.response.appendInt(this.mintables.size());
        for (Entry entry : this.mintables) {
            this.response.appendInt(entry.spriteId());
            this.response.appendInt(entry.mintable().startTime());
            this.response.appendInt(entry.mintable().endTime());
            this.response.appendBoolean(false);
            this.response.appendInt(entry.mintable().price());
            this.response.appendBoolean(false);
            this.response.appendShort(entry.wallItem() ? 1 : 0);
        }
        return this.response;
    }
}
