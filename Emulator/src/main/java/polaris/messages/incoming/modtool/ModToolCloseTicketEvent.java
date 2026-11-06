package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class ModToolCloseTicketEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int state = this.packet.readInt();
            int count = this.packet.readInt();

            if (state < 1 || state > 3 || !ModToolTicketGuard.isValidReleaseBatch(count)) {
                return;
            }

            // The window closes several issues with one resolution: every id is checked on its own.
            while (count-- > 0) {
                int ticketId = this.packet.readInt();

                if (!ModToolTicketGuard.isPositiveId(ticketId)) {
                    continue;
                }

                ModToolIssue issue =
                        Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

                if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getUser())) continue;

                User sender = Emulator.getGameEnvironment().getUserManager().getUser(issue.senderId);

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
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.ticket.close")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }
}
