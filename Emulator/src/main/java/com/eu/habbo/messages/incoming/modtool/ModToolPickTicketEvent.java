package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolTicketState;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModToolIssueInfoComposer;

public class ModToolPickTicketEvent extends MessageHandler {
    public static boolean send = false;

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            this.packet.readInt();
            int ticketId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(ticketId)) {
                this.client.getHabbo().alert(Emulator.getTexts().getValue("support.ticket.picked.failed"));
                return;
            }

            ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

            if (issue != null) {
                if (!ModToolTicketGuard.canPick(issue)) {
                    this.client.sendResponse(new ModToolIssueInfoComposer(issue));
                    this.client.getHabbo().alert(Emulator.getTexts().getValue("support.ticket.picked.failed"));

                    return;
                }

                //this.client.sendResponse(new ModToolIssueInfoComposer(issue));
                Emulator.getGameEnvironment().getModToolManager().pickTicket(issue, this.client.getHabbo());
            } else {
                this.client.getHabbo().alert(Emulator.getTexts().getValue("support.ticket.picked.failed"));
            }
        } else {
            ScripterManager.scripterDetected(this.client, Emulator.getTexts().getValue("scripter.warning.modtools.ticket.pick").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()));
        }
    }
}
