package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.CfhTopic;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.modtool.ModToolIssue;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolPreset;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;

public class ModToolIssueDefaultSanctionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            GameEnvironment environment = Emulator.getGameEnvironment();
            int issueId = this.packet.readInt();
            // The window sends the ids of the issues it groups with this one before the topic;
            // they are skipped here, only the issue that was picked is closed.
            int groupedCount = this.packet.readInt();

            if (!ModToolTicketGuard.isValidIdListSize(groupedCount)) {
                return;
            }

            for (int i = 0; i < groupedCount; i++) {
                this.packet.readInt();
            }

            int category = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(issueId) || !ModToolTicketGuard.isPositiveId(category)) {
                return;
            }

            ModToolIssue issue = environment.getModToolManager().getTicket(issueId);

            if (issue == null) {
                return;
            }

            if (issue.modId == this.client.getUser().getUserInfo().getId()) {
                CfhTopic modToolCategory = environment.getModToolManager().getCfhTopic(category);

                if (modToolCategory != null) {
                    ModToolPreset defaultSanction = modToolCategory.defaultSanction;

                    if (defaultSanction != null) {
                        User target = environment.getUserManager().getUser(issue.reportedId);

                        if (!ModToolManager.canModerateTarget(this.client.getUser(), issue.reportedId)) {
                            return;
                        }

                        if (defaultSanction.banLength > 0) {
                            environment
                                    .getModToolManager()
                                    .ban(
                                            issue.reportedId,
                                            this.client.getUser(),
                                            defaultSanction.message,
                                            ModToolManager.daysToSeconds(defaultSanction.banLength),
                                            ModToolBanType.ACCOUNT,
                                            modToolCategory.id);
                        } else if (defaultSanction.muteLength > 0) {

                            if (target != null) {
                                target.mute(ModToolManager.daysToSeconds(defaultSanction.muteLength), false);
                            }
                        }
                    }
                }

                // Closing it as handled also tells the reporter and stores the decision.
                User sender = environment.getUserManager().getUser(issue.senderId);
                environment.getModToolManager().closeTicketAsHandled(issue, sender);
            } else {
                this.client.getUser().alert(Emulator.getTexts().getValue("supporttools.not_ticket_owner"));
            }
        }
    }
}
