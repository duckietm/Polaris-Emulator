package polaris.messages.outgoing.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.modtool.ModToolBan;
import polaris.hotel.permissions.Rank;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.housekeeping.HousekeepingPrivacy;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class HousekeepingUserDetailComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingUserDetailComposer.class);

    private static final int CURRENCY_DUCKETS = 0;
    private static final int CURRENCY_DIAMONDS = 5;

    private final UserInfo info;

    public HousekeepingUserDetailComposer(UserInfo info) {
        this.info = info;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HousekeepingUserDetailComposer);

        if (this.info == null) {
            this.response.appendBoolean(false);
            return this.response;
        }

        GameEnvironment environment = Emulator.getGameEnvironment();
        int userId = this.info.getId();
        Rank rank = this.info.getRank();
        ModToolBan ban = environment.getModToolManager().checkForBan(userId);
        User online = environment.getUserManager().getUser(userId);
        StoredSettings stored = readStoredSettings(userId);
        int now = (int) (System.currentTimeMillis() / 1000L);

        // A muted user keeps the end time in memory until the next stats save.
        int muteEnd =
                online != null ? Math.max(online.getUserStats().getMuteEndTime(), stored.muteEnd()) : stored.muteEnd();
        int achievementScore = online != null ? online.getUserStats().getAchievementScore() : stored.achievementScore();

        this.response.appendBoolean(true);
        this.response.appendInt(userId);
        this.response.appendString(safe(this.info.getUsername()));
        this.response.appendString(safe(this.info.getMotto()));
        this.response.appendString(safe(this.info.getLook()));
        this.response.appendInt(rank != null ? rank.getId() : 0);
        this.response.appendString(rank != null ? safe(rank.getName()) : "");
        this.response.appendBoolean(this.info.isOnline());
        this.response.appendInt(this.info.getLastOnline());
        this.response.appendInt(this.info.getCredits());
        this.response.appendInt(this.info.getCurrencyAmount(CURRENCY_DUCKETS));
        this.response.appendInt(this.info.getCurrencyAmount(CURRENCY_DIAMONDS));
        // Masked for everyone, like the IP below; the clear addresses are the audited user.private list.
        this.response.appendString(
                HousekeepingPrivacy.mailHidden(rank) ? "" : HousekeepingPrivacy.maskMail(safe(this.info.getMail())));
        this.response.appendString(HousekeepingPrivacy.maskIp(safe(this.info.getIpLogin())));
        this.response.appendBoolean(ban != null);
        this.response.appendBoolean(muteEnd > now);
        // A trade lock is the can_trade flag, set from the latest sanction's trade_locked_until.
        boolean tradeLocked = online != null
                ? !online.getUserStats().allowTrade()
                : !stored.canTrade() || stored.tradeLockedUntil() > now;
        this.response.appendBoolean(tradeLocked);

        // Profile block, read by the renderer as optional trailing fields.
        this.response.appendInt(this.info.getAccountCreated());
        this.response.appendInt(achievementScore);
        this.response.appendInt(Messenger.getFriendCount(userId));
        this.response.appendInt(environment.getGuildManager().getGuilds(userId).size());

        List<UserBadge> badges = online != null
                ? new ArrayList<>(online.getInventory().getBadgesComponent().getWearingBadges())
                : BadgesComponent.getBadgesOfflineUser(userId);
        badges.sort(Comparator.comparingInt(UserBadge::getSlot));

        this.response.appendInt(badges.size());
        for (UserBadge badge : badges) {
            this.response.appendInt(badge.getSlot());
            this.response.appendString(safe(badge.getCode()));
        }

        return this.response;
    }

    /**
     * Reads the stored settings with a narrow select: loading UserStats for an
     * offline user would insert a users_settings row when none exists.
     */
    private static StoredSettings readStoredSettings(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT us.achievement_score, us.mute_end_timestamp, us.can_trade, "
                                + "COALESCE((SELECT MAX(s.trade_locked_until) FROM sanctions s WHERE s.habbo_id = us.user_id), 0) "
                                + "AS trade_locked_until FROM users_settings us WHERE us.user_id = ? LIMIT 1")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    return new StoredSettings(
                            set.getInt("achievement_score"),
                            set.getInt("mute_end_timestamp"),
                            !"0".equals(set.getString("can_trade")),
                            set.getInt("trade_locked_until"));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return new StoredSettings(0, 0, true, 0);
    }

    private record StoredSettings(int achievementScore, int muteEnd, boolean canTrade, int tradeLockedUntil) {}

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
