package com.eu.habbo.habbohotel.users.inventory;

import com.eu.habbo.Emulator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Place of each user on the total-badges leaderboard, for the badgesRank of
 * the RoomUsers packet. Same order as {@code BadgeLeaderboardHttpHandler}:
 * most badges first, then the lowest user id. Reloaded in the background so
 * composing a packet never waits on the query; unknown users rank -1.
 */
public final class BadgeRanks {

    private static final Logger LOGGER = LoggerFactory.getLogger(BadgeRanks.class);
    static final long CACHE_TTL_MS = 60_000L;
    public static final int UNKNOWN = -1;

    private static volatile Map<Integer, Integer> ranks = Collections.emptyMap();
    private static volatile long expiresAt = 0L;
    private static final AtomicBoolean LOADING = new AtomicBoolean(false);

    private BadgeRanks() {}

    public static int rankOf(int userId) {
        refreshIfStale();
        return ranks.getOrDefault(userId, UNKNOWN);
    }

    /** Ranks from (user id, badge count) rows, in leaderboard order. */
    static Map<Integer, Integer> rank(List<int[]> userTotals) {
        List<int[]> sorted = new ArrayList<>(userTotals);
        sorted.removeIf(row -> row[1] <= 0);
        sorted.sort((a, b) -> a[1] != b[1] ? Integer.compare(b[1], a[1]) : Integer.compare(a[0], b[0]));

        Map<Integer, Integer> result = new HashMap<>(sorted.size() * 2);
        for (int index = 0; index < sorted.size(); index++) {
            result.put(sorted.get(index)[0], index + 1);
        }
        return result;
    }

    private static void refreshIfStale() {
        if (expiresAt > System.currentTimeMillis() || !LOADING.compareAndSet(false, true)) {
            return;
        }

        Runnable load = () -> {
            try {
                List<int[]> totals = load();
                if (totals != null) {
                    ranks = rank(totals);
                }
            } finally {
                expiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
                LOADING.set(false);
            }
        };

        if (Emulator.getThreading() == null || Emulator.getThreading().run(load) == null) {
            expiresAt = System.currentTimeMillis() + CACHE_TTL_MS;
            LOADING.set(false);
        }
    }

    private static List<int[]> load() {
        List<int[]> totals = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT ub.user_id, COUNT(DISTINCT ub.badge_code) AS total_badges FROM users_badges ub "
                                + "INNER JOIN users u ON u.id = ub.user_id GROUP BY ub.user_id");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                totals.add(new int[] {set.getInt("user_id"), set.getInt("total_badges")});
            }
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error("Unable to load badge ranks", exception);
            return null;
        }
        return totals;
    }
}
