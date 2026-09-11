package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

/**
 * AIR 13 {@code ObjectRemoveMultiple} (official id 1451, parser
 * {@code class_2853}): {@code int count [ int id ] int pickerId}. The client
 * disposes every floor object of the batch and refreshes the tile object map
 * once, instead of once per {@code ObjectRemove}.
 */
public class RemoveFloorItemsComposer extends MessageComposer {

    private final Collection<Integer> itemIds;
    private final int pickerId;

    public RemoveFloorItemsComposer(Collection<Integer> itemIds, int pickerId) {
        this.itemIds = itemIds;
        this.pickerId = pickerId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ObjectRemoveMultipleComposer);

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
