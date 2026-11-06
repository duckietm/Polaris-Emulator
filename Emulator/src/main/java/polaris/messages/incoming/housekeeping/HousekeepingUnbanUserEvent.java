package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingUnbanUserEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.BANS;
    }

    private static final String ACTION_KEY = "user.unban";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int userId = this.packet.readInt();

        if (userId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        UserInfo info = Emulator.getGameEnvironment().getUserManager().getUserInfo(userId);

        if (info == null) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        // The UPDATE only touches active bans (ban_expire > now) the operator may lift, so calling it
        // on a never-banned user is a benign no-op that returns false.
        boolean cleared =
                Emulator.getGameEnvironment().getModToolManager().unban(info.getUsername(), this.client.getUser());

        if (cleared) {
            polaris.hotel.modtool.HousekeepingAuditLog.log(
                    this.client.getUser().getUserInfo().getId(),
                    this.client.getUser().getUserInfo().getUsername(),
                    ACTION_KEY,
                    userId,
                    "username=" + info.getUsername(),
                    this.client.getUser().getUserInfo().getIpLogin());
        }
        this.client.sendResponse(new HousekeepingActionResultComposer(
                ACTION_KEY, cleared, cleared ? userId : 0, cleared ? "" : "housekeeping.error.no_active_ban"));
    }
}
