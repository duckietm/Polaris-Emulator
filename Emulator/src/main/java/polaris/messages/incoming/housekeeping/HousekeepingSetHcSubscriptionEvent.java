package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Extend a user's HC by `days`. Adds to the existing club_expire_timestamp
 * if it's still in the future, otherwise stretches from `now`. Days==0
 * means cancel the active subscription (timestamp clamped to `now`).
 */
public class HousekeepingSetHcSubscriptionEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ECONOMY;
    }

    private static final String ACTION_KEY = "user.set_hc";
    private static final int SECONDS_IN_DAY = 24 * 3600;

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
        int days = this.packet.readInt();

        if (userId <= 0 || days < 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        if (!HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), userId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        int now = Emulator.getIntUnixTimestamp();
        int newExpire;

        User online = Emulator.getGameEnvironment().getUserManager().getUser(userId);

        int extension = (int) Math.min((long) days * SECONDS_IN_DAY, Integer.MAX_VALUE);

        if (days == 0) {
            newExpire = now;
        } else if (online != null) {
            int current = online.getUserStats().getClubExpireTimestamp();
            newExpire = HousekeepingSanctionDuration.unixUntil(current > now ? current : now, extension);
        } else {
            newExpire = -1; // offline: extended from the stored expiry, read below
        }

        if (online != null) {
            online.getUserStats().setClubExpireTimestamp(newExpire);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_settings SET club_expire_timestamp = ? WHERE user_id = ? LIMIT 1")) {
            if (newExpire < 0) {
                // A day added to an offline user with 300 days left used to leave them one day.
                newExpire = HousekeepingSanctionDuration.unixUntil(
                        Math.max(storedClubExpire(connection, userId), now), extension);
            }
            statement.setInt(1, newExpire);
            statement.setInt(2, userId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(
                        ACTION_KEY, false, 0, "housekeeping.error.user_not_found"));
                return;
            }
        } catch (SQLException e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                userId,
                "days=" + days + " expire=" + newExpire,
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, userId, ""));
    }

    private static int storedClubExpire(Connection connection, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT club_expire_timestamp FROM users_settings WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        }
    }
}
