package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormStartLobbyCounterComposer extends MessageComposer {

    private final int secondsUntilStart;

    public SnowStormStartLobbyCounterComposer(int secondsUntilStart) {
        this.secondsUntilStart = secondsUntilStart;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormStartLobbyCounterComposer);
        this.response.appendInt(this.secondsUntilStart);
        return this.response;
    }
}
