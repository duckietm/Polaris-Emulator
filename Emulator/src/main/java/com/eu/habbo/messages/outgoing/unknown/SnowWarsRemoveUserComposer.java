package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsRemoveUserComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2502);
        this.response.appendInt(3);
        return this.response;
    }
}
