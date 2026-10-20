package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.RoomAreaHideSupport;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class AreaHideComposer extends MessageComposer {
    private final HabboItem item;
    private final boolean on;

    public AreaHideComposer(HabboItem item) {
        this(item, RoomAreaHideSupport.isControllerActive(item));
    }

    /** {@code on} false lifts the area of a controller that is leaving the room while still switched on. */
    public AreaHideComposer(HabboItem item, boolean on) {
        this.item = item;
        this.on = on;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.AreaHideComposer);
        this.response.appendInt(BuildersClubItemIds.toWire(this.item.getId()));
        this.response.appendBoolean(this.on);
        this.response.appendInt(RoomAreaHideSupport.getRootX(this.item));
        this.response.appendInt(RoomAreaHideSupport.getRootY(this.item));
        this.response.appendInt(RoomAreaHideSupport.getWidth(this.item));
        this.response.appendInt(RoomAreaHideSupport.getLength(this.item));
        this.response.appendBoolean(RoomAreaHideSupport.isInverted(this.item));
        this.response.appendBoolean(RoomAreaHideSupport.includesWallItems(this.item));
        this.response.appendBoolean(RoomAreaHideSupport.isInvisibilityEnabled(this.item));
        return this.response;
    }
}
