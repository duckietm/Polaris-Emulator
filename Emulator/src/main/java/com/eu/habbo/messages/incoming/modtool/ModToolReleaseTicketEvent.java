package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ModToolReleaseTicketEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int count = this.packet.readInt();

            if (!ModToolTicketGuard.isValidReleaseBatch(count)) {
                return;
            }

            while (count != 0) {
                count--;

                int ticketId = this.packet.readInt();

                if (!ModToolTicketGuard.isPositiveId(ticketId)) {
                    continue;
                }

                ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

                if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getHabbo()))
                    continue;

                issue.modId = 0;
                issue.modName = "";
                issue.state = ModToolTicketState.OPEN;

                Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
            }
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.ticket.release").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
