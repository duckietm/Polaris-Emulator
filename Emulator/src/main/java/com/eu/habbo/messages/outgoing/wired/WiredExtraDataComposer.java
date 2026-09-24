package com.eu.habbo.messages.outgoing.wired;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableWebApi;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import com.eu.habbo.messages.outgoing.SecretBearingComposer;

public class WiredExtraDataComposer extends MessageComposer implements SecretBearingComposer {
    private final InteractionWiredExtra extra;
    private final Room room;
    private final Habbo viewer;

    public WiredExtraDataComposer(InteractionWiredExtra extra, Room room) {
        this(extra, room, null);
    }

    public WiredExtraDataComposer(InteractionWiredExtra extra, Room room, Habbo viewer) {
        this.extra = extra;
        this.room = room;
        this.viewer = viewer;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WiredEffectDataComposer);
        if (this.viewer != null) {
            this.extra.serializeWiredDataFor(this.response, this.room, this.viewer);
        } else {
            this.extra.serializeWiredData(this.response, this.room);
        }
        this.extra.needsUpdate(true);
        return this.response;
    }

    @Override
    public boolean carriesSecret() {
        return this.extra instanceof WiredExtraVariableWebApi;
    }
}
