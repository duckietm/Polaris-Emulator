package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormQuePositionComposer extends MessageComposer {

    private final int position;
    private final int queueSize;

    public SnowStormQuePositionComposer(int position, int queueSize) {
        this.position = position;
        this.queueSize = queueSize;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormQuePositionComposer);
        this.response.appendInt(this.position);
        this.response.appendInt(this.queueSize);
        return this.response;
    }
}
