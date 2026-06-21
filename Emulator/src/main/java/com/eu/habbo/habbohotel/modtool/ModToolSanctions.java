package com.eu.habbo.habbohotel.modtool;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.plugin.events.sanctions.SanctionEvent;
import gnu.trove.map.hash.THashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

public class ModToolSanctions {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModToolSanctions.class);

    private final THashMap<Integer, ArrayList<ModToolSanctionItem>> sanctionHashmap;
    private final THashMap<Integer, ModToolSanctionLevelItem> sanctionLevelsHashmap;

    public ModToolSanctions() {
        long millis = System.currentTimeMillis();
        this.sanctionHashmap = new THashMap<>();
        this.sanctionLevelsHashmap = new THashMap<>();
        this.loadModSanctions();

        LOGGER.info("Sanctions Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public synchronized void loadModSanctions() {
        this.sanctionHashmap.clear();
        this.sanctionLevelsHashmap.clear();

        this.loadSanctionLevels();
    }

    private void loadSanctionLevels() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM sanction_levels")) {
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.sanctionLevelsHashmap.put(set.getInt("level"), new ModToolSanctionLevelItem(set.getInt("level"), set.getString("type"), set.getInt("hour_length"), set.getInt("probation_days")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public ModToolSanctionLevelItem getSanctionLevelItem(int sanctionLevel) {
        return this.sanctionLevelsHashmap.get(sanctionLevel);
    }

    public THashMap<Integer, ArrayList<ModToolSanctionItem>> getSanctions(int habboId) {
        synchronized (this.sanctionHashmap) {
            this.sanctionHashmap.clear();
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM sanctions WHERE habbo_id = ? ORDER BY id ASC")) {
                statement.setInt(1, habboId);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        if (this.sanctionHashmap.get(set.getInt("habbo_id")) == null) {
                            this.sanctionHashmap.put(set.getInt("habbo_id"), new ArrayList<>());
                        }

                        ModToolSanctionItem item = new ModToolSanctionItem(set.getInt("id"), set.getInt("habbo_id"), set.getInt("sanction_level"), set.getInt("probation_timestamp"), set.getBoolean("is_muted"), set.getInt("mute_duration"), set.getInt("trade_locked_until"), set.getString("reason"));

                        if (!this.sanctionHashmap.get(set.getInt("habbo_id")).contains(item)) {
                            this.sanctionHashmap.get(set.getInt("habbo_id")).add(item);
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            return this.sanctionHashmap;
        }
    }

    private void insertSanction(int habboId, int sanctionLevel, int probationTimestamp, String reason, int tradeLockedUntil, boolean isMuted, int muteDuration) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO sanctions (habbo_id, sanction_level, probation_timestamp, reason, trade_locked_until, is_muted, mute_duration) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
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
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE sanctions SET probation_timestamp = ? WHERE id = ?")) {
            statement.setInt(1, probationTimestamp);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateTradeLockedUntil(int rowId, int tradeLockedUntil) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE sanctions SET trade_locked_until = ? WHERE id = ?")) {
            statement.setInt(1, tradeLockedUntil);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void updateMuteDuration(int rowId, int muteDuration) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE sanctions SET mute_duration = ? WHERE id = ?")) {
            statement.setInt(1, muteDuration);
            statement.setInt(2, rowId);

            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void run(int habboId, Habbo self, int sanctionLevel, int cfhTopic, String reason, int tradeLockedUntil, boolean isMuted, int muteDuration) {
        if (!ModToolManager.canModerateTarget(self, habboId)) {
            return;
        }

        sanctionLevel++;

        ModToolSanctionLevelItem sanctionLevelItem = getSanctionLevelItem(sanctionLevel);

        insertSanction(habboId, sanctionLevel, buildProbationTimestamp(sanctionLevelItem), reason, tradeLockedUntil, isMuted, muteDuration);

        runSanctionBasedOnLevel(sanctionLevelItem, habboId, reason, cfhTopic, self, muteDuration);

        Emulator.getPluginManager().fireEvent(new SanctionEvent(self, Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId), sanctionLevel));
    }

    private int buildProbationTimestamp(ModToolSanctionLevelItem sanctionLevelItem) {
        return Emulator.getIntUnixTimestamp() + (sanctionLevelItem.sanctionProbationDays * 24 * 60 * 60);
    }

    public int getProbationDays(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionProbationDays;
    }

    private void runSanctionBasedOnLevel(ModToolSanctionLevelItem sanctionLevelItem, int habboId, String reason, int cfhTopic, Habbo self, int muteDuration) {
        Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(habboId);

        int muteDurationSeconds = 0;

        if (muteDuration > 0) {
            Date muteDurationDate = new Date((long) muteDuration * 1000);
            long diff = muteDurationDate.getTime() - Emulator.getDate().getTime();
            muteDurationSeconds = Math.toIntExact(diff / 1000);
        }

        switch (sanctionLevelItem.sanctionType) {
            case "ALERT": habbo.alert(reason); break;
            case "BAN": Emulator.getGameEnvironment().getModToolManager().ban(habboId, self, reason, sanctionLevelItem.sanctionHourLength, ModToolBanType.ACCOUNT, cfhTopic); break;
            case "MUTE": habbo.mute(muteDurationSeconds == 0 ? 3600 : muteDurationSeconds, false); break;
            default: break;
        }
    }

    public String getSanctionType(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionType;
    }

    public int getTimeOfSanction(ModToolSanctionLevelItem sanctionLevelItem) {
        return sanctionLevelItem.sanctionHourLength;
    }
}
