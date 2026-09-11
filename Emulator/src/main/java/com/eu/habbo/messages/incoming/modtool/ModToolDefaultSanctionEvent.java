package com.eu.habbo.messages.incoming.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.CfhTopic;
import com.eu.habbo.habbohotel.modtool.ModToolBanType;
import com.eu.habbo.habbohotel.modtool.ModToolManager;
import com.eu.habbo.habbohotel.modtool.ModToolPreset;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.modtool.ModeratorActionResultComposer;

/**
 * Official {@code DefaultSanctionMessageComposer} (1681): applies the sanction
 * the selected CFH topic defines to a single account, without closing a ticket.
 * The client (`ModActionCtrl` / our `ModToolsUserModActionView`) sends
 * {@code (userId, categoryId, message)} plus a trailing issue id when the action
 * was started from a ticket.
 */
public class ModToolDefaultSanctionEvent extends MessageHandler {
    private static final int DAY_IN_SECONDS = 24 * 60 * 60;

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_SUPPORTTOOL)) {
            return;
        }

        int userId = this.packet.readInt();
        int categoryId = this.packet.readInt();
        String message = ModToolInputGuard.normalize(this.packet.readString());

        if (this.packet.bytesAvailable() >= 4) {
            this.packet.readInt(); // issue id: the ticket the action was started from, audited elsewhere
        }

        if (!ModToolTicketGuard.isPositiveId(userId) || !ModToolTicketGuard.isPositiveId(categoryId)) {
            return;
        }

        if (!ModToolManager.canModerateTarget(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
            return;
        }

        CfhTopic topic = Emulator.getGameEnvironment().getModToolManager().getCfhTopic(categoryId);

        if (topic == null || topic.defaultSanction == null) {
            this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
            return;
        }

        ModToolPreset sanction = topic.defaultSanction;
        String reason = ModToolInputGuard.isSafeMessage(message) ? message : sanction.message;
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (sanction.banLength > 0) {
            Emulator.getGameEnvironment()
                    .getModToolManager()
                    .ban(
                            userId,
                            this.client.getHabbo(),
                            reason,
                            sanction.banLength * DAY_IN_SECONDS,
                            ModToolBanType.ACCOUNT,
                            topic.id);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_bans");
        } else if (sanction.muteLength > 0) {
            if (target == null) {
                this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
                return;
            }

            target.mute(sanction.muteLength * DAY_IN_SECONDS, false);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
        } else {
            if (target == null) {
                this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
                return;
            }

            target.alert(reason);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
        }

        this.client.sendResponse(new ModeratorActionResultComposer(userId, true));
    }
}
