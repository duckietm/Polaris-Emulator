package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Apply an arbitrary-duration in-room mute. Habbo.mute is a session-only
 * mute (it stores remaining seconds on the live Habbo object), so the
 * target must be online for the action to take effect — when the target
 * isn't online the handler returns ok=false with `user_offline` so the
 * UI can fall back to ModToolSanctionMute or surface a clear error.
 */
public class HousekeepingMuteUserEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.mute";

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
        String reason = this.packet.readString();
        int minutes = this.packet.readInt();

        if (userId <= 0 || minutes <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (target == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_offline"));
            return;
        }

        target.mute(HousekeepingSanctionDuration.secondsFromMinutes(minutes), false);

        if (reason != null && !reason.isEmpty()) {
            target.alert(reason);
        }

        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
