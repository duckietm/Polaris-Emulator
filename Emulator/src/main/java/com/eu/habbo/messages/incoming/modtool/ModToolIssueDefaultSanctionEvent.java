package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.*;
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
            int issueId = this.packet.readInt();
            this.packet.readInt();
            int category = this.packet.readInt();

            if (!ModToolTicketGuard.isPositiveId(issueId) || !ModToolTicketGuard.isPositiveId(category)) {
                return;
            }

            ModToolIssue issue = Emulator.getGameEnvironment().getModToolManager().getTicket(issueId);

            if (issue == null) {
                return;
            }

            if (issue.modId == this.client.getHabbo().getHabboInfo().getId()) {
                CfhTopic modToolCategory = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(category);

                if (modToolCategory != null) {
                    ModToolPreset defaultSanction = modToolCategory.defaultSanction;

                    if (defaultSanction != null) {
                        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(issue.reportedId);

                        if (!ModToolManager.canModerateTarget(this.client.getHabbo(), issue.reportedId)) {
                            return;
                        }

                        if (defaultSanction.banLength > 0) {
                            Emulator.getGameEnvironment().getModToolManager().ban(issue.reportedId, this.client.getHabbo(), defaultSanction.message, defaultSanction.banLength * 86400, ModToolBanType.ACCOUNT, modToolCategory.id);
                        } else if (defaultSanction.muteLength > 0) {

                            if (target != null) {
                                target.mute(defaultSanction.muteLength * 86400, false);
                            }
                        }
                    }
                }

                issue.state = ModToolTicketState.CLOSED;
                Emulator.getGameEnvironment().getModToolManager().updateTicketToMods(issue);
            } else {
                this.client.getHabbo().alert(Emulator.getTexts().getValue("supporttools.not_ticket_owner"));
            }
        }
    }
}
