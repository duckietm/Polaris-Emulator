package com.eu.habbo.messages.outgoing.habbicons;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public final class HabbiconResultComposer extends MessageComposer {
    private final int action;
    private final int id;
    private final int error;

    public HabbiconResultComposer(int action, int id, int error) {
        this.action = action;
        this.id = id;
        this.error = error;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HabbiconResultComposer);
        this.response.appendInt(action);
        this.response.appendInt(id);
        this.response.appendInt(error);
        return this.response;
    }
}
