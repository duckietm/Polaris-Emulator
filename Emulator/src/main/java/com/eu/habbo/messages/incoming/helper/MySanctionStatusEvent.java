package com.eu.habbo.messages.incoming.helper;

import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolSanctionInfoComposer;

public class MySanctionStatusEvent extends MessageHandler {

    @Override
    public void handle() throws Exception {
        this.packet.readBoolean(); // pending-only selector used by the client help view
        this.client.sendResponse(new ModToolSanctionInfoComposer(this.client.getHabbo()));
    }
}
