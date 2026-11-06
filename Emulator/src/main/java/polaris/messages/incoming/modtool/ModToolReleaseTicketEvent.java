package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolTicketState;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;

public class ModToolReleaseTicketEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
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

                ModToolIssue issue =
                        Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

                if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getUser())) continue;

                issue.modId = 0;
                issue.modName = "";
                issue.state = ModToolTicketState.OPEN;

                Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.ticket.release")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
