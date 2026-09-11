package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionPreview;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.threading.runnables.UpdateModToolIssue;

/**
 * Official {@code ModToolSanctionMessageComposer} (issueId, accountId, topicId):
 * {@code IssueManager.requestSanctionData} sends it when the ticket window opens
 * and every time the CFH topic dropdown changes, while
 * {@code requestSanctionDataForAccount} sends it with issueId -1 from the
 * standalone mod-action window. The answer is the sanction preview
 * ({@code ModToolSanctionDataComposer}, 2782).
 */
public class ModToolIssueChangeTopicEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
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

                if (!ModToolTicketGuard.isOwnedBy(issue, this.client.getHabbo())) {
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
