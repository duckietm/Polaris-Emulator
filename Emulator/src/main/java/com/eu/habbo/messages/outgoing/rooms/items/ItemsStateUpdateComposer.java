package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Collection;

/**
 * AIR 13 {@code ItemsStateUpdate} (official id 3697, parser
 * {@code class_3328}): {@code int count [ int id str itemData ]}, the batched
 * form of the single wall-item state update.
 */
public class ItemsStateUpdateComposer extends MessageComposer {

    private final Collection<HabboItem> items;

    public ItemsStateUpdateComposer(Collection<HabboItem> items) {
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemsStateUpdateComposer);

        this.response.appendInt(this.items.size());
        for (HabboItem item : this.items) {
            this.response.appendInt(item.getId());
            this.response.appendString(item.getExtradata());
        }

        return this.response;
    }

    public Collection<HabboItem> getItems() {
        return this.items;
    }
}
