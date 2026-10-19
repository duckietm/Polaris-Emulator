package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Set;

public class ItemsDataUpdateComposer extends MessageComposer {
    private final Set<HabboItem> items;

    public ItemsDataUpdateComposer(Set<HabboItem> items) {
        this.items = items;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ItemsDataUpdateComposer);
        this.response.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            this.response.appendInt(BuildersClubItemIds.toWire(item.getId()));
            item.serializeExtradata(this.response);
        }

        return this.response;
    }

    public Set<HabboItem> getItems() {
        return items;
    }
}
