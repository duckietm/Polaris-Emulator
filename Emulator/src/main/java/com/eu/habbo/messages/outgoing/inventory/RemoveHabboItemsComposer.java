package com.eu.habbo.messages.outgoing.inventory;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

/**
 * AIR 13 {@code FurniListRemoveMultiple} (official id 2813, parser
 * {@code class_3613}): {@code int count [ int stripId ]}. The client drops the
 * whole batch from the furni model in one pass and resets the unseen items
 * once.
 */
public class RemoveHabboItemsComposer extends MessageComposer {

    private final Collection<Integer> itemIds;

    public RemoveHabboItemsComposer(Collection<Integer> itemIds) {
        this.itemIds = itemIds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.FurniListRemoveMultipleComposer);

        this.response.appendInt(this.itemIds.size());
        for (Integer itemId : this.itemIds) {
            this.response.appendInt(itemId);
        }

        return this.response;
    }

    public Collection<Integer> getItemIds() {
        return this.itemIds;
    }
}
