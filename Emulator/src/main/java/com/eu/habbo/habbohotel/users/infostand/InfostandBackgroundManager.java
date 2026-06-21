package com.eu.habbo.habbohotel.users.infostand;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class InfostandBackgroundManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfostandBackgroundManager.class);

    public enum Category {
        BACKGROUND("background"),
        STAND("stand"),
        OVERLAY("overlay"),
        CARD("card"),
        BORDER("border");

        public final String dbValue;

        Category(String dbValue) {
            this.dbValue = dbValue;
        }

        public static Category fromDbValue(String value) {
            for (Category category : values()) {
                if (category.dbValue.equalsIgnoreCase(value)) return category;
            }
            return null;
        }
    }

    private final Map<Category, Map<Integer, Entry>> entries = new EnumMap<>(Category.class);
    private boolean enforce = false;

    public InfostandBackgroundManager() {
        for (Category category : Category.values()) {
            this.entries.put(category, Collections.emptyMap());
        }

        this.reload();
    }

    public void reload() {
        Map<Category, Map<Integer, Entry>> next = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            next.put(category, new HashMap<>());
        }

        int loaded = 0;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT id, category, min_rank, is_hc_only, is_ambassador_only FROM infostand_backgrounds");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                Category category = Category.fromDbValue(set.getString("category"));
                if (category == null) continue;

                int id = set.getInt("id");
                int minRank = set.getInt("min_rank");
                boolean isHcOnly = set.getBoolean("is_hc_only");
                boolean isAmbassadorOnly = set.getBoolean("is_ambassador_only");

                next.get(category).put(id, new Entry(minRank, isHcOnly, isAmbassadorOnly));
                loaded++;
            }
        } catch (SQLException e) {
            this.enforce = false;
            for (Category category : Category.values()) {
                this.entries.put(category, Collections.emptyMap());
            }
            LOGGER.error("InfostandBackgroundManager -> Failed to load infostand_backgrounds, server-side validation disabled.", e);
            return;
        }

        for (Category category : Category.values()) {
            this.entries.put(category, next.get(category));
        }

        this.enforce = loaded > 0;

        if (this.enforce) {
            LOGGER.info(summary(
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size()));
            LOGGER.debug("Infostand Background Manager assets: {} bg, {} stands, {} overlays, {} cards, {} borders",
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size());
        } else {
            LOGGER.info("InfostandBackgroundManager -> infostand_backgrounds is empty, server-side validation disabled (only range clamp will apply).");
        }
    }

    static String summary(int backgrounds, int stands, int overlays, int cards, int borders) {
        int total = backgrounds + stands + overlays + cards + borders;
        return String.format("Infostand Background Manager -> Loaded! (%d assets)", total);
    }

    public boolean canUse(Habbo habbo, Category category, int id) {
        if (id == 0) return true;
        if (!this.enforce) return true;
        if (habbo == null) return false;

        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        if (categoryEntries == null) return false;

        Entry entry = categoryEntries.get(id);
        if (entry == null) return false;

        HabboInfo info = habbo.getHabboInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        HabboStats stats = habbo.getHabboStats();
        boolean hasClub = stats != null && stats.hasActiveClub();

        if (entry.isHcOnly && !hasClub) return false;
        if (entry.isAmbassadorOnly && !habbo.hasPermission(Permission.ACC_AMBASSADOR)) return false;
        if (rankId < entry.minRank) return false;

        return true;
    }

    public static final class Entry {
        public final int minRank;
        public final boolean isHcOnly;
        public final boolean isAmbassadorOnly;

        public Entry(int minRank, boolean isHcOnly, boolean isAmbassadorOnly) {
            this.minRank = minRank;
            this.isHcOnly = isHcOnly;
            this.isAmbassadorOnly = isAmbassadorOnly;
        }
    }
}
