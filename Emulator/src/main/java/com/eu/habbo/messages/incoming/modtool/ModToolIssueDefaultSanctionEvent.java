package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolPreset;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;

public class ModToolIssueDefaultSanctionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
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

            if (issue.modId == this.client.getHabbo().getHabboInfo().getId()) {
                CfhTopic modToolCategory = environment.getModToolManager().getCfhTopic(category);

                if (modToolCategory != null) {
                    ModToolPreset defaultSanction = modToolCategory.defaultSanction;

                    if (defaultSanction != null) {
                        Habbo target = environment.getHabboManager().getHabbo(issue.reportedId);

                        if (!ModToolManager.canModerateTarget(this.client.getHabbo(), issue.reportedId)) {
                            return;
                        }

                        if (defaultSanction.banLength > 0) {
                            environment
                                    .getModToolManager()
                                    .ban(
                                            issue.reportedId,
                                            this.client.getHabbo(),
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
                Habbo sender = environment.getHabboManager().getHabbo(issue.senderId);
                environment.getModToolManager().closeTicketAsHandled(issue, sender);
            } else {
                this.client.getHabbo().alert(Emulator.getTexts().getValue("supporttools.not_ticket_owner"));
            }
        }
    }
}
