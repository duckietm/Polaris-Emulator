package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;

import java.util.Collection;

/** Sends a transient, client-side opacity update for Wired-selected furniture. */
public class WiredFurniOpacityComposer extends MessageComposer {
    private final Collection<HabboItem> items;
    private final int opacity;
    private final boolean clickThrough;
    private final int easing;

    public WiredFurniOpacityComposer(Collection<HabboItem> items, int opacity, boolean clickThrough, int easing) {
        this.items = items;
        this.opacity = opacity;
        this.clickThrough = clickThrough;
        this.easing = easing;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredFurniOpacityComposer);
        this.response.appendInt(1);
        this.response.appendInt(this.resolveRoomId());
        this.response.appendInt(this.items.size());

        for (HabboItem item : this.items) {
            this.response.appendInt(item.getId());
            this.response.appendBoolean(item.getBaseItem() != null && item.getBaseItem().getType() != FurnitureType.FLOOR);
            this.response.appendInt(this.opacity);
            this.response.appendBoolean(this.clickThrough);
            this.response.appendInt(this.easing);
            this.response.appendInt(WiredMovementsComposer.DEFAULT_DURATION);
        }

        return this.response;
    }

    private int resolveRoomId() {
        for (HabboItem item : this.items) {
            if (item != null) {
                return Math.max(0, item.getRoomId());
            }
        }

        return 0;
    }
}
