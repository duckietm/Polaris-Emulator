package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsUnknownComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2869);
        this.response.appendString("snowwar");
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendBoolean(true);
        this.response.appendBoolean(true);
        this.response.appendInt(0);
        this.response.appendInt(0);
        this.response.appendInt(0);
        return this.response;
    }
}
