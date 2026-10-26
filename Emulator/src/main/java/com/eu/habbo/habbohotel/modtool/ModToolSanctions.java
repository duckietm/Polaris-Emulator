package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.sanctions.SanctionEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModToolSanctions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModToolSanctions.class);

    private final Map<Integer, ModToolSanctionLevelItem> sanctionLevelsHashmap;

    public ModToolSanctions() {
        long millis = System.currentTimeMillis();
        this.sanctionLevelsHashmap = new HashMap<>();
        this.loadModSanctions();

        LOGGER.info("Sanctions Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public synchronized void loadModSanctions() {
        this.sanctionLevelsHashmap.clear();

        this.loadSanctionLevels();
    }

    private void loadSanctionLevels() {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM sanction_levels")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.sanctionLevelsHashmap.put(
                            set.getInt("level"),
                            new ModToolSanctionLevelItem(
                                    set.getInt("level"),
                                    set.getString("type"),
                                    set.getInt("hour_length"),
                                    set.getInt("probation_days")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    /** Every query of this class opens its connection here. */
    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    private static int now() {
        return Emulator.getIntUnixTimestamp();
    }

    /**
     * The level that follows {@code lastLevel}, held at the highest level configured below it: a user
     * already at the top level stays there instead of reaching a level that does not exist. Null only
     * when no level at or below the next one is configured at all.
     */
    static ModToolSanctionLevelItem nextLevel(Map<Integer, ModToolSanctionLevelItem> levels, int lastLevel) {
        int wanted = lastLevel + 1;
        ModToolSanctionLevelItem exact = levels.get(wanted);
        if (exact != null) {
            return exact;
        }

        ModToolSanctionLevelItem highest = null;
        for (ModToolSanctionLevelItem level : levels.values()) {
            if (level.sanctionLevel <= wanted && (highest == null || level.sanctionLevel > highest.sanctionLevel)) {
                highest = level;
            }
        }
        return highest;
    }

    public ModToolSanctionLevelItem getSanctionLevelItem(int sanctionLevel) {
        return this.sanctionLevelsHashmap.get(sanctionLevel);
    }

    /**
     * The sanctions of one user, oldest first, keyed by the user id. Every call builds its own map: a map shared
     * between calls was cleared by one caller while another was still reading it.
     */
    public Map<Integer, ArrayList<ModToolSanctionItem>> getSanctions(int habboId) {
        Map<Integer, ArrayList<ModToolSanctionItem>> sanctions = new HashMap<>();
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM sanctions WHERE habbo_id = ? ORDER BY id ASC")) {
            statement.setInt(1, habboId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    sanctions
                            .computeIfAbsent(set.getInt("habbo_id"), id -> new ArrayList<>())
                            .add(new ModToolSanctionItem(
                                    set.getInt("id"),
                                    set.getInt("habbo_id"),
                                    set.getInt("sanction_level"),
                                    set.getInt("probation_timestamp"),
                                    set.getBoolean("is_muted"),
                                    set.getInt("mute_duration"),
                                    set.getInt("trade_locked_until"),
                                    set.getString("reason")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return sanctions;
    }

    private void insertSanction(
            int habboId,
            int sanctionLevel,
            int probationTimestamp,
            String reason,
            int tradeLockedUntil,
            boolean isMuted,
            int muteDuration) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO sanctions (habbo_id, sanction_level, probation_timestamp, reason, trade_locked_until, is_muted, mute_duration) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            statement.setInt(1, habboId);
            statement.setInt(2, sanctionLevel);
            statement.setInt(3, probationTimestamp);
            statement.setString(4, reason);
            statement.setInt(5, tradeLockedUntil);
            statement.setBoolean(6, isMuted);
            statement.setInt(7, muteDuration);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateSanction(int rowId, int probationTimestamp) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE sanctions SET probation_timestamp = ? WHERE id = ?")) {
            statement.setInt(1, probationTimestamp);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateTradeLockedUntil(int rowId, int tradeLockedUntil) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE sanctions SET trade_locked_until = ? WHERE id = ?")) {
            statement.setInt(1, tradeLockedUntil);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateMuteDuration(int rowId, int muteDuration) {
        try (Connection connection = connection();
                PreparedStatement statement =
                        connection.prepareStatement("UPDATE sanctions SET mute_duration = ? WHERE id = ?")) {
            statement.setInt(1, muteDuration);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void run(
            int habboId,
            Habbo self,
            int sanctionLevel,
            int cfhTopic,
            String reason,
            int tradeLockedUntil,
            boolean isMuted,
            int muteDuration) {
        if (!ModToolManager.canModerateTarget(self, habboId)) {
            return;
        }

        // A user past the last configured level used to reach a level that does not exist, and the
        // sanction threw before anything was applied.
        ModToolSanctionLevelItem sanctionLevelItem = nextLevel(this.sanctionLevelsHashmap, sanctionLevel);
        if (sanctionLevelItem == null) {
            LOGGER.warn(
                    "No sanction level is configured after level {}; user {} was not sanctioned",
                    sanctionLevel,
                    habboId);
            return;
        }

        insertSanction(
                habboId,
                sanctionLevelItem.sanctionLevel,
                buildProbationTimestamp(sanctionLevelItem),
                reason,
                tradeLockedUntil,
                isMuted,
                muteDuration);

        Habbo habbo = runSanctionBasedOnLevel(sanctionLevelItem, habboId, reason, cfhTopic, self, muteDuration);

        Emulator.getPluginManager().fireEvent(new SanctionEvent(self, habbo, sanctionLevelItem.sanctionLevel));
    }

    private int buildProbationTimestamp(ModToolSanctionLevelItem sanctionLevelItem) {
        return ModToolManager.banExpiry(now(), ModToolManager.daysToSeconds(sanctionLevelItem.sanctionProbationDays));
    }

    public int getProbationDays(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionProbationDays;
    }

    /** Runs the level's sanction and answers the target when they are online, null otherwise. */
    private Habbo runSanctionBasedOnLevel(
            ModToolSanctionLevelItem sanctionLevelItem,
            int habboId,
            String reason,
            int cfhTopic,
            Habbo self,
            int muteDuration) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);

        int muteDurationSeconds = 0;

        if (muteDuration > 0) {
            Date muteDurationDate = new Date((long) muteDuration * 1000);
            long diff = muteDurationDate.getTime() - Emulator.getDate().getTime();
            muteDurationSeconds = Math.toIntExact(diff / 1000);
        }

        switch (sanctionLevelItem.sanctionType) {
            case "ALERT":
                // An offline user has nobody to alert; the sanction row is their record.
                if (habbo != null) {
                    habbo.alert(reason);
                }
                break;
            case "BAN":
                Emulator.getGameEnvironment()
                        .getModToolManager()
                        .ban(
                                habboId,
                                self,
                                reason,
                                ModToolManager.hoursToSeconds(sanctionLevelItem.sanctionHourLength),
                                ModToolBanType.ACCOUNT,
                                cfhTopic);
                break;
            case "MUTE":
                int seconds = muteDurationSeconds <= 0 ? 3600 : muteDurationSeconds;
                if (habbo != null) {
                    habbo.mute(seconds, false);
                } else {
                    muteOffline(habboId, seconds);
                }
                break;
            default:
                break;
        }

        return habbo;
    }

    /** Extends an offline user's mute in their settings row, where the next login reads it. */
    private static void muteOffline(int habboId, int seconds) {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_settings SET mute_end_timestamp = LEAST(GREATEST(mute_end_timestamp, ?) + ?, 2147483647) WHERE user_id = ? LIMIT 1")) {
            statement.setInt(1, now());
            statement.setInt(2, seconds);
            statement.setInt(3, habboId);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public String getSanctionType(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionType;
    }

    public int getTimeOfSanction(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionHourLength;
    }
}
