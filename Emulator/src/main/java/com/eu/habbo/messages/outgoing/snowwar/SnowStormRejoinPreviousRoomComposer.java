package com.eu.habbo.messages.outgoing.snowwar;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

public class SnowStormRejoinPreviousRoomComposer extends MessageComposer {

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.SnowStormRejoinPreviousRoomComposer);
        return this.response;
    }
}
