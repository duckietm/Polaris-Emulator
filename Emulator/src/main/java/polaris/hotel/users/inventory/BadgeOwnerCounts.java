package polaris.hotel.users.inventory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

/**
 * How many users hold each badge, refreshed lazily every
 * {@link #CACHE_TTL_MS}; feeds the owner count and rarity tier of the
 * UserCurrentBadges packet without a query per composed packet.
 */
public final class BadgeOwnerCounts {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeOwnerCounts.class);
    static final long CACHE_TTL_MS = 60_000L;

    private static volatile Map<String, Integer> counts = Collections.emptyMap();
    private static volatile long expiresAt = 0L;

    private BadgeOwnerCounts() {}

    public static int ownerCount(String badgeCode) {
        if (badgeCode == null) {
            return 0;
        }
        return snapshot().getOrDefault(badgeCode, 0);
    }

    /** Drops the cached counts so the next lookup reloads them. */
    public static void invalidate() {
        expiresAt = 0L;
    }

    private static Map<String, Integer> snapshot() {
        long now = System.currentTimeMillis();
        if (expiresAt > now) {
            return counts;
        }
        synchronized (BadgeOwnerCounts.class) {
            if (expiresAt > now) {
                return counts;
            }
            Map<String, Integer> loaded = load();
            if (loaded != null) {
                counts = loaded;
            }
            expiresAt = now + CACHE_TTL_MS;
            return counts;
        }
    }

    private static Map<String, Integer> load() {
        Map<String, Integer> loaded = new HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT badge_code, COUNT(DISTINCT user_id) AS owner_count FROM users_badges GROUP BY badge_code");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                loaded.put(set.getString("badge_code"), set.getInt("owner_count"));
            }
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error("Unable to load badge owner counts", exception);
            return null;
        }
        return loaded;
    }
}
