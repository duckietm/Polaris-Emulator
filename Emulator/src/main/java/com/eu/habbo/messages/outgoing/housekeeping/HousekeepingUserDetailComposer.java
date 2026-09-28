package com.eu.habbo.messages.outgoing.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.modtool.ModToolBan;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HousekeepingUserDetailComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HousekeepingUserDetailComposer.class);

    private static final int CURRENCY_DUCKETS = 0;
    private static final int CURRENCY_DIAMONDS = 5;

    private final HabboInfo info;

    public HousekeepingUserDetailComposer(HabboInfo info) {
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
        Habbo online = environment.getHabboManager().getHabbo(userId);
        StoredSettings stored = readStoredSettings(userId);
        int now = (int) (System.currentTimeMillis() / 1000L);

        // A muted user keeps the end time in memory until the next stats save.
        int muteEnd =
                online != null ? Math.max(online.getHabboStats().getMuteEndTime(), stored.muteEnd()) : stored.muteEnd();
        int achievementScore =
                online != null ? online.getHabboStats().getAchievementScore() : stored.achievementScore();

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
        this.response.appendString(safe(this.info.getMail()));
        this.response.appendString(safe(this.info.getIpLogin()));
        this.response.appendBoolean(ban != null);
        this.response.appendBoolean(muteEnd > now);
        // A trade lock is the can_trade flag, set from the latest sanction's trade_locked_until.
        boolean tradeLocked = online != null
                ? !online.getHabboStats().allowTrade()
                : !stored.canTrade() || stored.tradeLockedUntil() > now;
        this.response.appendBoolean(tradeLocked);

        // Profile block, read by the renderer as optional trailing fields.
        this.response.appendInt(this.info.getAccountCreated());
        this.response.appendInt(achievementScore);
        this.response.appendInt(Messenger.getFriendCount(userId));
        this.response.appendInt(environment.getGuildManager().getGuilds(userId).size());

        List<HabboBadge> badges = online != null
                ? new ArrayList<>(online.getInventory().getBadgesComponent().getWearingBadges())
                : BadgesComponent.getBadgesOfflineHabbo(userId);
        badges.sort(Comparator.comparingInt(HabboBadge::getSlot));

        this.response.appendInt(badges.size());
        for (HabboBadge badge : badges) {
            this.response.appendInt(badge.getSlot());
            this.response.appendString(safe(badge.getCode()));
        }

        return this.response;
    }

    /**
     * Reads the stored settings with a narrow select: loading HabboStats for an
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
