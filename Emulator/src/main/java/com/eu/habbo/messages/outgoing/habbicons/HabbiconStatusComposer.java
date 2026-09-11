package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class HabbiconStatusComposer extends MessageComposer {
    private final int id;
    private final int state;

    public HabbiconStatusComposer(int id, int state) {
        this.id = id;
        this.state = state;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconStatusComposer);
        this.response.appendInt(id);
        this.response.appendInt(state);
        return this.response;
    }
}
