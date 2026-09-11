package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.unknown.UnknownHelperComposer;
import java.util.List;

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
        Habbo habbo = this.client.getHabbo();

        if (habbo == null) return;

        List<ModToolIssue> pending =
                Emulator.getGameEnvironment().getModToolManager().openTicketsForHabbo(habbo);

        for (ModToolIssue issue : pending) {
            Emulator.getGameEnvironment().getModToolManager().closeTicketAsWithdrawn(issue);
        }

        this.client.sendResponse(new UnknownHelperComposer());
    }
}
