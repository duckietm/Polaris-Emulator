package com.eu.habbo.messages.outgoing.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctionLevelItem;
import com.eu.habbo.habbohotel.modtool.ModToolSanctions;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ModToolUserInfoComposer extends MessageComposer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModToolUserInfoComposer.class);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private final ResultSet set;
    private final boolean hideMail;

    public ModToolUserInfoComposer(ResultSet set, boolean hideMail) {
        this.set = set;
        this.hideMail = hideMail;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.ModToolUserInfoComposer);
        try {
            int userId = this.set.getInt("user_id");
            String machineId = this.set.getString("machine_id");
            int now = Emulator.getIntUnixTimestamp();

            int totalBans = countBansForUser(userId);
            int lastPurchaseTimestamp = fetchLastPurchaseTimestamp(userId);
            int tradeLockExpiryTimestamp = fetchActiveTradeLockExpiry(userId, now);
            int identityRelatedBanCount = countIdentityRelatedBans(userId, machineId);

            this.response.appendInt(userId);
            this.response.appendString(this.set.getString("username"));
            this.response.appendString(this.set.getString("look"));
            this.response.appendInt((now - this.set.getInt("account_created")) / 60);
            this.response.appendInt((this.set.getInt("online") == 1 ? 0 : now - this.set.getInt("last_online")) / 60);
            this.response.appendBoolean(this.set.getInt("online") == 1);
            this.response.appendInt(this.set.getInt("cfh_send"));
            this.response.appendInt(this.set.getInt("cfh_abusive"));
            this.response.appendInt(this.set.getInt("cfh_warnings"));
            this.response.appendInt(totalBans); // Number of bans
            this.response.appendInt(this.set.getInt("tradelock_amount"));
            this.response.appendString(formatUnixTimestamp(tradeLockExpiryTimestamp)); // Trading lock expiry timestamp
            this.response.appendString(formatUnixTimestamp(lastPurchaseTimestamp));   // Last Purchase Timestamp
            this.response.appendInt(userId); //Personal Identification #
            this.response.appendInt(identityRelatedBanCount); // Number of account bans on the same machine_id
            this.response.appendString(this.hideMail ? "" : this.set.getString("mail"));
            this.response.appendString("Rank (" + this.set.getInt("rank_id") + "): " + this.set.getString("rank_name")); //user_class_txt

            ModToolSanctions modToolSanctions = Emulator.getGameEnvironment().getModToolSanctions();

            if (Emulator.getConfig().getBoolean("hotel.sanctions.enabled")) {
                THashMap<Integer, ArrayList<ModToolSanctionItem>> modToolSanctionItemsHashMap = Emulator.getGameEnvironment().getModToolSanctions().getSanctions(this.set.getInt("user_id"));
                ArrayList<ModToolSanctionItem> modToolSanctionItems = modToolSanctionItemsHashMap.get(this.set.getInt("user_id"));

                if (modToolSanctionItems != null && modToolSanctionItems.size() > 0) //has sanction
                {
                    ModToolSanctionItem item = modToolSanctionItems.get(modToolSanctionItems.size() - 1);
                    ModToolSanctionLevelItem modToolSanctionLevelItem = modToolSanctions.getSanctionLevelItem(item.sanctionLevel);

                    this.response.appendString(modToolSanctions.getSanctionType(modToolSanctionLevelItem));
                    this.response.appendInt(31);
                }

            }

            return this.response;
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return null;
    }

    public ResultSet getSet() {
        return set;
    }

    private static int countBansForUser(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) AS amount FROM bans WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("amount");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return 0;
    }

    /**
     * Most recent purchase timestamp from logs_shop_purchases for this
     * user. Returns 0 when the user has never bought anything (in which
     * case the wire field stays empty and the client shows the empty
     * placeholder).
     */
    private static int fetchLastPurchaseTimestamp(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT MAX(`timestamp`) AS ts FROM logs_shop_purchases WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("ts");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return 0;
    }

    /**
     * Latest active trade-lock expiry from the sanctions table. Only
     * locks expiring in the future are considered — past entries don't
     * count. Returns 0 when no active lock exists.
     */
    private static int fetchActiveTradeLockExpiry(int userId, int now) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT MAX(trade_locked_until) AS expiry FROM sanctions WHERE habbo_id = ? AND trade_locked_until > ?")) {
            statement.setInt(1, userId);
            statement.setInt(2, now);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("expiry");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return 0;
    }

    /**
     * Count of OTHER user accounts that have been banned from the same
     * machine_id as this user. An empty machine_id (default '') is
     * ignored — never matches anything by definition. Self is excluded
     * because the user's own bans are already counted under banCount.
     */
    private static int countIdentityRelatedBans(int userId, String machineId) {
        if (machineId == null || machineId.isEmpty()) return 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(DISTINCT user_id) AS amount FROM bans WHERE machine_id = ? AND user_id != ?")) {
            statement.setString(1, machineId);
            statement.setInt(2, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) return set.getInt("amount");
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        return 0;
    }

    /**
     * Wire format for date fields is `yyyy-MM-dd HH:mm`. A 0 timestamp
     * is rendered as an empty string so the client falls back to its
     * empty-state placeholder.
     */
    private static String formatUnixTimestamp(int timestamp) {
        if (timestamp <= 0) return "";
        return DATE_FORMAT.format(new Date(timestamp * 1000L));
    }
}
