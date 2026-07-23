package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;

public class SnowWarsUserChatComposer extends MessageComposer {
    @Override
    protected ServerMessage composeInternal() {
        this.response.init(2049);
        this.response.appendInt(1); //UserID
        this.response.appendString("Message");
        return this.response;
    }
}
