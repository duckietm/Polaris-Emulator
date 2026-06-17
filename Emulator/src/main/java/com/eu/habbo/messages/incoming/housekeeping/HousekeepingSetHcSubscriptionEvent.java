package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Extend a user's HC by `days`. Adds to the existing club_expire_timestamp
 * if it's still in the future, otherwise stretches from `now`. Days==0
 * means cancel the active subscription (timestamp clamped to `now`).
 */
public class HousekeepingSetHcSubscriptionEvent extends MessageHandler {
    private static final String ACTION_KEY = "user.set_hc";
    private static final int SECONDS_IN_DAY = 24 * 3600;

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
        int days = this.packet.readInt();

        if (userId <= 0 || days < 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getHabbo(), userId)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int now = Emulator.getIntUnixTimestamp();
        int newExpire;

        Habbo online = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);

        if (days == 0) {
            newExpire = now;
        } else if (online != null) {
            int current = online.getHabboStats().getClubExpireTimestamp();
            newExpire = (current > now ? current : now) + (days * SECONDS_IN_DAY);
        } else {
            newExpire = now + (days * SECONDS_IN_DAY); // best-effort offline; can't read previous expiry cheaply
        }

        if (online != null) {
            online.getHabboStats().setClubExpireTimestamp(newExpire);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, newExpire);
            statement.setInt(2, userId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
                return;
            }
        } catch (SQLException e) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, userId, "days=" + days + " expire=" + newExpire,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }
}
