package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ReportRoomFormComposer;

/**
 * Despite the name this is the help window asking for the calls for help the sender still has
 * open (header 3267): the answer lists them, and the client offers to keep or discard them.
 */
public class RequestReportRoomEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        this.client.sendResponse(new ReportRoomFormComposer(
                Emulator.getGameEnvironment().getModToolManager().openTicketsForUser(this.client.getUser())));
    }
}
