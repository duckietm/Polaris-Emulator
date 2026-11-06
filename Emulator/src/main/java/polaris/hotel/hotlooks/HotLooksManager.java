package polaris.hotel.hotlooks;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;

/**
 * Serves the avatar editor "hot looks" tab from the {@code hot_looks} table.
 *
 * <p>The official client caps the tab at 20 looks per gender. The table is the admin surface: rows are
 * re-read once the cache is older than {@code hotlooks.cache.seconds} (default 300), so an edit shows up
 * without a restart.
 */
public class HotLooksManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(HotLooksManager.class);
    private static final int DEFAULT_CACHE_SECONDS = 300;
    public static final int MAX_PER_GENDER = 20;

    private volatile List<HotLook> cached = List.of();
    private volatile long loadedAt = 0L;

    public HotLooksManager() {
        this.reload();
    }

    public synchronized void reload() {
        List<HotLook> loaded = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT gender, figure FROM hot_looks WHERE enabled = 1 ORDER BY sort_order ASC, id ASC");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                HotLook look = new HotLook(HotLook.normalizeGender(set.getString("gender")), set.getString("figure"));
                if (look.isUsable()) {
                    loaded.add(look);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        this.cached = List.copyOf(loaded);
        this.loadedAt = System.currentTimeMillis();
    }

    /** Looks of one gender, capped like the official tab; refreshes the cache when it is stale. */
    public List<HotLook> getHotLooks(String gender, int limit) {
        if (this.isStale()) {
            this.reload();
        }
        return select(this.cached, HotLook.normalizeGender(gender), limit);
    }

    /** Pure selection used by {@link #getHotLooks(String, int)}: filter by gender and cap the count. */
    public static List<HotLook> select(List<HotLook> looks, String gender, int limit) {
        int cap = Math.min(limit <= 0 ? MAX_PER_GENDER : limit, MAX_PER_GENDER);
        List<HotLook> result = new ArrayList<>();
        for (HotLook look : looks) {
            if (result.size() >= cap) {
                break;
            }
            if (look.gender().equals(gender) && look.isUsable()) {
                result.add(look);
            }
        }
        return result;
    }

    private boolean isStale() {
        int seconds = Emulator.getConfig().getInt("hotlooks.cache.seconds", DEFAULT_CACHE_SECONDS);
        return System.currentTimeMillis() - this.loadedAt > seconds * 1000L;
    }
}
