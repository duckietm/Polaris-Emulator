package polaris.messages.incoming.modtool;

import java.util.List;
import polaris.Emulator;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.CallForHelpPendingCallsDeletedComposer;

/**
 * Discards the calls for help the sender still has open. The client asks for them when the help
 * window opens ({@link RequestReportRoomEvent}) and offers to keep or discard them; discarding lands
 * here, and the empty answer tells the client the pending calls are gone.
 */
public class DeletePendingCallsForHelpEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();

        if (user == null) return;

        List<ModToolIssue> pending =
                Emulator.getGameEnvironment().getModToolManager().openTicketsForUser(user);

        for (ModToolIssue issue : pending) {
            Emulator.getGameEnvironment().getModToolManager().closeTicketAsWithdrawn(issue);
        }

        this.client.sendResponse(new CallForHelpPendingCallsDeletedComposer());
    }
}
