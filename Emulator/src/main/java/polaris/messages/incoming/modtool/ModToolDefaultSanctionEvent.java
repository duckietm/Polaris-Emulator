package polaris.messages.incoming.modtool;

import polaris.Emulator;
import polaris.hotel.modtool.CfhTopic;
import polaris.hotel.modtool.ModToolBanType;
import polaris.hotel.modtool.ModToolManager;
import polaris.hotel.modtool.ModToolPreset;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.modtool.ModeratorActionResultComposer;

/**
 * Official {@code DefaultSanctionMessageComposer} (1681): applies the sanction
 * the selected CFH topic defines to a single account, without closing a ticket.
 * The client (`ModActionCtrl` / our `ModToolsUserModActionView`) sends
 * {@code (userId, categoryId, message)} plus a trailing issue id when the action
 * was started from a ticket.
 */
public class ModToolDefaultSanctionEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getUser().hasPermission(Permission.ACC_SUPPORTTOOL)) {
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

        if (!ModToolManager.canModerateTarget(this.client.getUser(), userId)) {
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
        User target = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        if (sanction.banLength > 0) {
            Emulator.getGameEnvironment()
                    .getModToolManager()
                    .ban(
                            userId,
                            this.client.getUser(),
                            reason,
                            ModToolManager.daysToSeconds(sanction.banLength),
                            ModToolBanType.ACCOUNT,
                            topic.id);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_bans");
        } else if (sanction.muteLength > 0) {
            if (target == null) {
                this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
                return;
            }

            target.mute(ModToolManager.daysToSeconds(sanction.muteLength), false);
            int muteHours = (int) Math.min(Integer.MAX_VALUE, sanction.muteLength * 24L);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
            ModToolManager.recordCaution(
                    userId, this.client.getUser().getUserInfo().getId(), "MUTE", reason, muteHours);
        } else {
            if (target == null) {
                this.client.sendResponse(new ModeratorActionResultComposer(userId, false));
                return;
            }

            target.alert(reason);
            ModToolManager.bumpUserSettingCounter(userId, "cfh_warnings");
            ModToolManager.recordCaution(
                    userId, this.client.getUser().getUserInfo().getId(), "ALERT", reason, 0);
        }

        this.client.sendResponse(new ModeratorActionResultComposer(userId, true));
    }
}
