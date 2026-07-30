package com.eu.habbo.habbohotel.games.snowwar;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Persistent SnowStorm scores in the AIR leaderboard wire shape. */
public final class SnowWarLeaderboardRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(SnowWarLeaderboardRepository.class);
    private static final int GAME_TYPE_ID = 0;
    private final SnowWarConnectionProvider connections;

    SnowWarLeaderboardRepository(SnowWarConnectionProvider connections) {
        this.connections = connections;
    }

    public record Entry(int userId, int score, int rank, String name, String figure, String gender) {}

    public record Page(
            int year,
            int week,
            int maxOffset,
            int currentOffset,
            int minutesUntilReset,
            List<Entry> entries,
            int totalListSize,
            int gameTypeId) {}

    public void recordScores(List<SnowWarGamePlayer> players) {
        if (players.isEmpty()) {
            return;
        }

        LocalDate weekStart = weekStart(0);
        String sql = "INSERT INTO snowwar_scores (user_id, week_start, score, matches) VALUES (?, ?, ?, 1) "
                + "ON DUPLICATE KEY UPDATE score = score + VALUES(score), matches = matches + 1";
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (SnowWarGamePlayer player : players) {
                statement.setInt(1, player.getUserId());
                statement.setDate(2, Date.valueOf(weekStart));
                statement.setInt(
                        3, Math.max(0, player.getAttributes().getScore().get()));
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            LOGGER.error("Unable to persist SnowWar leaderboard scores", exception);
        }
    }

    public Page load(int viewerUserId, boolean weekly, boolean friendsOnly, int weekOffset, int startRank, int limit) {
        int maxOffset = this.maxWeekOffset();
        int safeOffset = Math.max(0, Math.min(weekOffset, maxOffset));
        int safeRank = Math.max(1, startRank);
        int safeLimit = Math.max(1, Math.min(limit, 50));
        LocalDate selectedWeek = weekStart(safeOffset);
        List<Entry> entries =
                this.loadEntries(viewerUserId, weekly, friendsOnly, selectedWeek, safeRank, safeLimit, null);

        // AIR's public tables append the viewer when they are outside the
        // visible window, allowing the UI to highlight their actual rank.
        if (!friendsOnly && entries.stream().noneMatch(entry -> entry.userId() == viewerUserId)) {
            List<Entry> own = this.loadEntries(viewerUserId, weekly, false, selectedWeek, 1, 1, viewerUserId);
            if (!own.isEmpty()) {
                entries.add(own.get(0));
            }
        }

        return new Page(
                selectedWeek.get(WeekFields.ISO.weekBasedYear()),
                selectedWeek.get(WeekFields.ISO.weekOfWeekBasedYear()),
                maxOffset,
                safeOffset,
                minutesUntilReset(),
                entries,
                this.countEntries(viewerUserId, weekly, friendsOnly, selectedWeek),
                GAME_TYPE_ID);
    }

    private List<Entry> loadEntries(
            int viewerUserId,
            boolean weekly,
            boolean friendsOnly,
            LocalDate selectedWeek,
            int startRank,
            int limit,
            Integer onlyUserId) {
        String scores = weekly
                ? "SELECT user_id, score FROM snowwar_scores WHERE week_start = ?"
                : "SELECT user_id, SUM(score) score FROM snowwar_scores GROUP BY user_id";
        String sql = "WITH scores AS (" + scores + "), ranked AS ("
                + "SELECT user_id, score, RANK() OVER (ORDER BY score DESC) position FROM scores WHERE score > 0) "
                + "SELECT ranked.user_id, ranked.score, ranked.position, users.username, users.look, users.gender "
                + "FROM ranked INNER JOIN users ON users.id = ranked.user_id WHERE "
                + (onlyUserId == null ? "ranked.position >= ?" : "ranked.user_id = ?")
                + (friendsOnly
                        ? " AND (ranked.user_id = ? OR EXISTS (SELECT 1 FROM messenger_friendships "
                                + "WHERE user_one_id = ? AND user_two_id = ranked.user_id))"
                        : "")
                + " ORDER BY ranked.position, ranked.user_id LIMIT ?";

        List<Entry> entries = new ArrayList<>();
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (weekly) {
                statement.setDate(index++, Date.valueOf(selectedWeek));
            }
            statement.setInt(index++, onlyUserId == null ? startRank : onlyUserId);
            if (friendsOnly) {
                statement.setInt(index++, viewerUserId);
                statement.setInt(index++, viewerUserId);
            }
            statement.setInt(index, limit);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    entries.add(new Entry(
                            result.getInt("user_id"),
                            clampScore(result.getLong("score")),
                            result.getInt("position"),
                            result.getString("username"),
                            result.getString("look"),
                            result.getString("gender")));
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to load SnowWar leaderboard", exception);
        }
        return entries;
    }

    private int countEntries(int viewerUserId, boolean weekly, boolean friendsOnly, LocalDate selectedWeek) {
        String scores = weekly
                ? "SELECT user_id, score FROM snowwar_scores WHERE week_start = ?"
                : "SELECT user_id, SUM(score) score FROM snowwar_scores GROUP BY user_id";
        String sql = "WITH scores AS (" + scores + ") SELECT COUNT(*) FROM scores WHERE score > 0"
                + (friendsOnly
                        ? " AND (user_id = ? OR EXISTS (SELECT 1 FROM messenger_friendships "
                                + "WHERE user_one_id = ? AND user_two_id = scores.user_id))"
                        : "");
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            if (weekly) {
                statement.setDate(index++, Date.valueOf(selectedWeek));
            }
            if (friendsOnly) {
                statement.setInt(index++, viewerUserId);
                statement.setInt(index, viewerUserId);
            }
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            LOGGER.error("Unable to count SnowWar leaderboard entries", exception);
            return 0;
        }
    }

    private int maxWeekOffset() {
        try (Connection connection = this.connections.openConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT MIN(week_start) FROM snowwar_scores");
                ResultSet result = statement.executeQuery()) {
            if (!result.next() || result.getDate(1) == null) {
                return 0;
            }
            return Math.max(0, (int) ChronoUnit.WEEKS.between(result.getDate(1).toLocalDate(), weekStart(0)));
        } catch (SQLException exception) {
            LOGGER.error("Unable to determine SnowWar leaderboard history", exception);
            return 0;
        }
    }

    static LocalDate weekStart(int offset) {
        return LocalDate.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .minusWeeks(Math.max(0, offset));
    }

    static int minutesUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        ZonedDateTime reset =
                now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(ZoneOffset.UTC);
        return Math.max(0, (int) Duration.between(now, reset).toMinutes());
    }

    private static int clampScore(long score) {
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0, score));
    }
}
