package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ModToolCloseTicketEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int state = this.packet.readInt();
            this.packet.readInt();
            int ticketId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(ticketId) || state < 1 || state > 3) {
                return;
            }

            ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

            if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getHabbo()))
                return;

            Habbo sender = Emulator.getGameEnvironment().getHabboManager().getHabbo(issue.senderId);

            switch (state) {
                case 1:
                    Emulator.getGameEnvironment().getModToolManager().closeTicketAsUseless(issue, sender);
                    break;

                case 2:
                    Emulator.getGameEnvironment().getModToolManager().closeTicketAsAbusive(issue, sender);
                    break;

                case 3:
                    Emulator.getGameEnvironment().getModToolManager().closeTicketAsHandled(issue, sender);
                    break;
            }
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.ticket.close").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
