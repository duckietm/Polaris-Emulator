package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * AIR {@code ItemRemove}: {@code string id, int pickerId, bool byInvisibleFurniManager}; the flag as in {@link
 * RemoveFloorItemComposer}.
 */
public class RemoveWallItemComposer extends MessageComposer {
    private final HabboItem item;
    private final boolean byInvisibleFurniManager;

    public RemoveWallItemComposer(HabboItem item) {
        this(item, false);
    }

    public RemoveWallItemComposer(HabboItem item, boolean byInvisibleFurniManager) {
        this.item = item;
        this.byInvisibleFurniManager = byInvisibleFurniManager;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RemoveWallItemComposer);
        this.response.appendString(BuildersClubItemIds.toWire(this.item.getId()) + "");
        this.response.appendInt(this.item.getUserId());
        this.response.appendBoolean(this.byInvisibleFurniManager);
        return this.response;
    }

    public HabboItem getItem() {
        return item;
    }

    public boolean isByInvisibleFurniManager() {
        return byInvisibleFurniManager;
    }
}
