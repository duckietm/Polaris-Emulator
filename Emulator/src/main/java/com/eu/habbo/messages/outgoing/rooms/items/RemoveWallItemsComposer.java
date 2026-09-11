package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

/**
 * AIR 13 {@code ItemRemoveMultiple} (official id 2204, parser
 * {@code class_2992}): the wall-item twin of {@link RemoveFloorItemsComposer}.
 */
public class RemoveWallItemsComposer extends MessageComposer {

    private final Collection<Integer> itemIds;
    private final int pickerId;

    public RemoveWallItemsComposer(Collection<Integer> itemIds, int pickerId) {
        this.itemIds = itemIds;
        this.pickerId = pickerId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemRemoveMultipleComposer);

        this.response.appendInt(this.itemIds.size());
        for (Integer itemId : this.itemIds) {
            this.response.appendInt(itemId);
        }
        this.response.appendInt(this.pickerId);

        return this.response;
    }

    public Collection<Integer> getItemIds() {
        return this.itemIds;
    }

    public int getPickerId() {
        return this.pickerId;
    }
}
