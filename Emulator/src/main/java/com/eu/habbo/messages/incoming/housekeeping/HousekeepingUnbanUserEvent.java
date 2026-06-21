package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingUnbanUserEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.unban";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int userId = this.packet.readInt();

        if (userId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        HabboInfo info = Emulator.getGameEnvironment().getHabboManager().getHabboInfo(userId);

        if (info == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        // ModToolManager.unban only takes a username; the SQL UPDATE
        // happens against active bans (ban_expire > now), so calling it
        // on a never-banned user is a benign no-op that returns false.
        boolean cleared = Emulator.getGameEnvironment().getModToolManager().unban(info.getUsername());

        if (cleared) {
            com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                    this.client.getHabbo().getHabboInfo().getId(),
                    this.client.getHabbo().getHabboInfo().getUsername(),
                    ACTION_KEY, userId, "username=" + info.getUsername(),
                    this.client.getHabbo().getHabboInfo().getIpLogin());
        }
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, cleared, cleared ? userId : 0, cleared ? "" : "housekeeping.error.no_active_ban"));
    }
}
