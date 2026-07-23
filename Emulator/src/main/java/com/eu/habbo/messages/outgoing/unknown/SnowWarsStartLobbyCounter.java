package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsStartLobbyCounter extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(3757);
        this.response.appendInt(5);
        return this.response;
    }
}
