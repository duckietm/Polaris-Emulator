package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormGameEndedComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormGameEndedComposer);
        return this.response;
    }
}
