package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

/**
 * Error codes: 1 = queue full, 2 = already in game, 3 = not enough players,
 * 4 = no tickets, 5 = internal error (PROTOCOL.md 5028).
 */
public class SnowStormGenericErrorComposer extends MessageComposer {

    private final int errorCode;

    public SnowStormGenericErrorComposer(int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGenericErrorComposer);
        this.response.appendInt(this.errorCode);
        return this.response;
    }
}
