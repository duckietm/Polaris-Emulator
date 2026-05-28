package com.eu.habbo.messages.outgoing.wheel;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

// The winning prize id. The client animates the wheel to that slice; the reward
// was already granted server-side.
public class WheelResultComposer extends MessageComposer {
    private final int prizeId;

    public WheelResultComposer(int prizeId) {
        this.prizeId = prizeId;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.WheelResultComposer);
        this.response.appendInt(this.prizeId);
        return this.response;
    }
}
