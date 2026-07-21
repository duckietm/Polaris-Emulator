package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormOnStageRunningComposer extends MessageComposer {

    private final int totalSecondsLeft;

    public SnowStormOnStageRunningComposer(int totalSecondsLeft) {
        this.totalSecondsLeft = totalSecondsLeft;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormOnStageRunningComposer);
        this.response.appendInt(this.totalSecondsLeft);
        return this.response;
    }
}
