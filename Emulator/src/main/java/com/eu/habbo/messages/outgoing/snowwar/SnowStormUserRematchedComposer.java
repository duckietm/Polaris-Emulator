package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormUserRematchedComposer extends MessageComposer {

    private final int userId;

    public SnowStormUserRematchedComposer(int userId) {
        this.userId = userId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormUserRematchedComposer);
        this.response.appendInt(this.userId);
        return this.response;
    }
}
