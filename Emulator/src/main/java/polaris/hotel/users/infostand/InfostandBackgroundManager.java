package polaris.hotel.users.infostand;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;

public class InfostandBackgroundManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(InfostandBackgroundManager.class);

    public enum Category {
        BACKGROUND("background"),
        STAND("stand"),
        OVERLAY("overlay"),
        CARD("card"),
        BORDER("border"),
        BANNER("banner"),
        ACCENT("accent");

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
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, category, min_rank, is_hc_only, is_ambassador_only FROM infostand_backgrounds");
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
            LOGGER.error(
                    "InfostandBackgroundManager -> Failed to load infostand_backgrounds, server-side validation disabled.",
                    e);
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
                    this.entries.get(Category.BORDER).size(),
                    this.entries.get(Category.BANNER).size(),
                    this.entries.get(Category.ACCENT).size()));
            LOGGER.debug(
                    "Infostand Background Manager assets: {} bg, {} stands, {} overlays, {} cards, {} borders, {} banners, {} accents",
                    this.entries.get(Category.BACKGROUND).size(),
                    this.entries.get(Category.STAND).size(),
                    this.entries.get(Category.OVERLAY).size(),
                    this.entries.get(Category.CARD).size(),
                    this.entries.get(Category.BORDER).size(),
                    this.entries.get(Category.BANNER).size(),
                    this.entries.get(Category.ACCENT).size());
        } else {
            LOGGER.info(
                    "InfostandBackgroundManager -> infostand_backgrounds is empty, server-side validation disabled (only range clamp will apply).");
        }
    }

    static String summary(int... categorySizes) {
        int total = 0;
        for (int size : categorySizes) {
            total += size;
        }
        return String.format("Infostand Background Manager -> Loaded! (%d assets)", total);
    }

    public boolean canUse(User user, Category category, int id) {
        if (id == 0) return true;
        if (!this.enforce) return true;
        if (user == null) return false;

        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        if (categoryEntries == null) return false;

        Entry entry = categoryEntries.get(id);
        if (entry == null) return false;

        UserInfo info = user.getUserInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        UserStats stats = user.getUserStats();
        boolean hasClub = stats != null && stats.hasActiveClub();

        if (entry.isHcOnly && !hasClub) return false;
        if (entry.isAmbassadorOnly && !user.hasPermission(Permission.ACC_AMBASSADOR)) return false;
        if (rankId < entry.minRank) return false;

        return true;
    }

    /**
     * Why a user may not pick an id: "unknown" (not in the table), "hc", "ambassador" or "rank"; null when they may.
     * Mirrors {@link #canUse}, so the client can show a lock and its reason instead of a choice that is ignored.
     */
    public String denialReason(User user, Category category, int id) {
        if (id == 0 || !this.enforce) return null;
        if (user == null) return "unknown";

        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        Entry entry = categoryEntries == null ? null : categoryEntries.get(id);
        if (entry == null) return "unknown";

        UserInfo info = user.getUserInfo();
        int rankId = (info != null && info.getRank() != null) ? info.getRank().getId() : 0;
        UserStats stats = user.getUserStats();

        if (entry.isHcOnly && !(stats != null && stats.hasActiveClub())) return "hc";
        if (entry.isAmbassadorOnly && !user.hasPermission(Permission.ACC_AMBASSADOR)) return "ambassador";
        if (rankId < entry.minRank) return "rank";
        return null;
    }

    /** The ids listed for a category, with their rules; empty when the table is not enforced. */
    public Map<Integer, Entry> entries(Category category) {
        Map<Integer, Entry> categoryEntries = this.entries.get(category);
        return categoryEntries == null ? Collections.emptyMap() : Collections.unmodifiableMap(categoryEntries);
    }

    public boolean isEnforced() {
        return this.enforce;
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
