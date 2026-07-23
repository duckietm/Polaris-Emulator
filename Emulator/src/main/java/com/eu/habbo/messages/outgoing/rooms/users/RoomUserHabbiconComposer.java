package com.eu.habbo.messages.outgoing.rooms.users;

import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class RoomUserHabbiconComposer extends MessageComposer {
    private final RoomUnit roomUnit;
    private final int habbiconId;

    public RoomUserHabbiconComposer(RoomUnit roomUnit, int habbiconId) {
        this.roomUnit = roomUnit;
        this.habbiconId = habbiconId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RoomUserHabbiconComposer);
        this.response.appendInt(this.roomUnit.getId());
        this.response.appendInt(this.habbiconId);
        return this.response;
    }
}
