package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolSanctionPreview;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModToolIssueInfoComposer;
import polaris.messages.outgoing.modtool.ModToolIssuePickFailedComposer;

public class ModToolPickTicketEvent extends MessageHandler {
    public static boolean send = false;

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            // The window picks several issues at once: the count comes first, then the ids.
            int count = this.packet.readInt();

            if (!ModToolTicketGuard.isValidReleaseBatch(count)) {
                return;
            }

            while (count-- > 0) {
                this.pick(this.packet.readInt());
            }
        } else {
            ScripterManager.scripterDetected(
                    this.client,
                    Emulator.getTexts()
                            .getValue("scripter.warning.modtools.ticket.pick")
                            .replace(
                                    "%username%",
                                    this.client.getUser().getUserInfo().getUsername()));
        }
    }

    private void pick(int ticketId) {
        if (!ModToolTicketGuard.isPositiveId(ticketId)) {
            this.client.getUser().alert(Emulator.getTexts().getValue("support.ticket.picked.failed"));
            return;
        }

        ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

        if (issue == null) {
            this.client.getUser().alert(Emulator.getTexts().getValue("support.ticket.picked.failed"));
            return;
        }

        if (!ModToolTicketGuard.canPick(issue)) {
            // Somebody else got there first: the window says who, instead of a bare alert.
            this.client.sendResponse(new ModToolIssueInfoComposer(issue));
            this.client.sendResponse(new ModToolIssuePickFailedComposer(issue));

            return;
        }

        Emulator.getGameEnvironment().getModToolManager().pickTicket(issue, this.client.getUser());

        // IssueHandler.initialize() asks for the sanction preview as soon as the ticket
        // window opens, so send it unprompted instead of leaving the field blank.
        this.client.sendResponse(ModToolSanctionPreview.forTopic(issue.id, -1, issue.category, issue.reportedId));
    }
}
