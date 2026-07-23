package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormOnStageStartComposer extends MessageComposer {

    private final int preparingSeconds;

    public SnowStormOnStageStartComposer(int preparingSeconds) {
        this.preparingSeconds = preparingSeconds;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnStageStartComposer);
        this.response.appendInt(this.preparingSeconds);
        return this.response;
    }
}
