package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolSanctionPreview;
import polaris.hotel.permissions.Permission;
import polaris.messages.incoming.MessageHandler;
import polaris.threading.runnables.UpdateModToolIssue;

/**
 * Official {@code ModToolSanctionMessageComposer} (issueId, accountId, topicId):
 * {@code IssueManager.requestSanctionData} sends it when the ticket window opens
 * and every time the CFH topic dropdown changes, while
 * {@code requestSanctionDataForAccount} sends it with issueId -1 from the
 * standalone mod-action window. The answer is the sanction preview
 * ({@code CfhSanctionComposer}, 2782).
 */
public class ModToolIssueChangeTopicEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            int ticketId = this.packet.readInt();
            int accountId = this.packet.readInt();
            int categoryId = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(categoryId)) {
                return;
            }

            if (Emulator.getGameEnvironment().getModToolManager().getCfhTopic(categoryId) == null) {
                return;
            }

            int targetId = accountId;

            if (ModToolTicketGuard.isPositiveId(ticketId)) {
                ModToolIssue issue =
                        Emulator.getGameEnvironment().getModToolManager().getTicket(ticketId);

                if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getUser())) {
                    return;
                }

                issue.category = categoryId;
                new UpdateModToolIssue(issue).run();
                Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
                targetId = issue.reportedId;
            }

            this.client.sendResponse(ModToolSanctionPreview.forTopic(ticketId, accountId, categoryId, targetId));
        }
    }
}
