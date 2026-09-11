package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ReportRoomFormComposer;

/**
 * Despite the name this is the help window asking for the calls for help the sender still has
 * open (header 3267): the answer lists them, and the client offers to keep or discard them.
 */
public class RequestReportRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new ReportRoomFormComposer(
                Emulator.getGameEnvironment().getModToolManager().openTicketsForHabbo(this.client.getHabbo())));
    }
}
