package com.eu.habbo.messages.outgoing.rooms;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Tells the client whether the build height widget may be used in the room it just entered, and the
 * range its slider covers. The widget stays hidden until this says it is available, so the underpass
 * switch that lives inside it depends on this packet too.
 */
public class BuildHeightAvailableComposer extends MessageComposer {
    private final boolean available;

    public BuildHeightAvailableComposer(boolean available) {
        this.available = available;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.BuildHeightAvailableComposer);
        this.response.appendBoolean(this.available);
        this.response.appendInt(0);
        this.response.appendInt((int) Room.MAXIMUM_FURNI_HEIGHT);
        return this.response;
    }
}
