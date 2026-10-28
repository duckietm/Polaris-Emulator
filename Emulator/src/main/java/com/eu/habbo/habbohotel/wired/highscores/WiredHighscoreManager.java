package com.eu.habbo.habbohotel.wired.highscores;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.util.HotelDateTimeUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WiredHighscoreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHighscoreManager.class);

    private final ConcurrentHashMap<Integer, List<WiredHighscoreDataEntry>> data = new ConcurrentHashMap<>();

    private static final String locale =
            (System.getProperty("user.language") != null ? System.getProperty("user.language") : "en");
    private static final String country =
            (System.getProperty("user.country") != null ? System.getProperty("user.country") : "US");

    private static final DayOfWeek firstDayOfWeek =
            WeekFields.of(Locale.of(locale, country)).getFirstDayOfWeek();
    public static ScheduledFuture<?> midnightUpdater = null;

    // Every write to items_highscore_data goes through one thread, in the order it was asked for. On the
    // shared pool two rewrites of the same board ran side by side and left duplicate or lost rows that
    // came back at the next start.
    private final ExecutorService writer = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("Polaris-Highscores", 0).factory());

    public void load() {
        long millis = System.currentTimeMillis();

        this.data.clear();
        this.loadHighscoreData();

        LOGGER.info(
                "Highscore Manager -> Loaded! ({} MS, {} items)",
                System.currentTimeMillis() - millis,
                this.data.size());
    }

    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }

        midnightUpdater = Emulator.getThreading()
                .run(new WiredHighscoreMidnightUpdater(), WiredHighscoreMidnightUpdater.getNextUpdaterRun());
    }

    public void dispose() {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }

        // Writes already asked for still reach the database.
        this.writer.shutdown();
        try {
            this.writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        this.data.clear();
    }

    /** Runs a database write after every write asked for before it. */
    void write(Runnable write) {
        try {
            this.writer.execute(() -> {
                try {
                    write.run();
                } catch (RuntimeException e) {
                    LOGGER.error("Highscore write failed", e);
                }
            });
        } catch (RejectedExecutionException e) {
            LOGGER.warn("Highscore write dropped: the highscore manager is shutting down");
        }
    }

    /**
     * Adds points to the row these users hold on the board, or files a new row for them. The row is
     * found and changed under the board's lock and only that row is written: the give-points box used to
     * copy the board without the lock, add to its copy and rewrite the whole board, so concurrent games
     * and boxes lost each other's points, and the sum could overflow.
     */
    public void addScore(int itemId, List<Integer> userIds, int amount, int timestamp) {
        List<WiredHighscoreDataEntry> list =
                this.data.computeIfAbsent(itemId, k -> Collections.synchronizedList(new ArrayList<>()));
        WiredHighscoreDataEntry previous = null;
        WiredHighscoreDataEntry updated;

        synchronized (list) {
            Set<Integer> wanted = new HashSet<>(userIds);
            int index = -1;
            for (int i = 0; i < list.size(); i++) {
                WiredHighscoreDataEntry entry = list.get(i);
                if (entry != null && new HashSet<>(entry.getUserIds()).equals(wanted)) {
                    index = i;
                    break;
                }
            }

            if (index < 0) {
                updated = new WiredHighscoreDataEntry(itemId, userIds, amount, true, timestamp);
                list.add(updated);
            } else {
                previous = list.get(index);
                updated = new WiredHighscoreDataEntry(
                        itemId, previous.getUserIds(), saturatedSum(previous.getScore(), amount), true, timestamp);
                list.set(index, updated);
            }
        }

        WiredHighscoreDataEntry row = updated;
        WiredHighscoreDataEntry replaced = previous;
        this.write(() -> {
            try (Connection connection = connection()) {
                if (replaced == null) {
                    insert(connection, row);
                    return;
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE `items_highscore_data` SET `score` = ?, `timestamp` = ? WHERE `item_id` = ? AND `user_ids` = ? AND `score` = ? LIMIT 1")) {
                    update.setInt(1, row.getScore());
                    update.setInt(2, row.getTimestamp());
                    update.setInt(3, row.getItemId());
                    update.setString(4, joinUserIds(replaced.getUserIds()));
                    update.setInt(5, replaced.getScore());
                    if (update.executeUpdate() == 0) {
                        insert(connection, row);
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    /** Every query of this class opens its connection here. */
    private static Connection connection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    /** {@code a + b}, held at the int limits instead of wrapping around. */
    static int saturatedSum(int a, int b) {
        long sum = (long) a + b;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (sum < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) sum);
    }

    private static void insert(Connection connection, WiredHighscoreDataEntry entry) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO `items_highscore_data` (`item_id`, `user_ids`, `score`, `is_win`, `timestamp`) VALUES (?, ?, ?, ?, ?)")) {
            statement.setInt(1, entry.getItemId());
            statement.setString(2, joinUserIds(entry.getUserIds()));
            statement.setInt(3, entry.getScore());
            statement.setInt(4, entry.isWin() ? 1 : 0);
            statement.setInt(5, entry.getTimestamp());
            statement.execute();
        }
    }

    private void loadHighscoreData() {
        try (Connection connection = connection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_highscore_data")) {
            statement.setFetchSize(1000);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    try {
                        WiredHighscoreDataEntry entry = new WiredHighscoreDataEntry(set);

                        this.data
                                .computeIfAbsent(
                                        entry.getItemId(), k -> Collections.synchronizedList(new ArrayList<>()))
                                .add(entry);
                    } catch (RuntimeException e) {
                        // Skip a single malformed row instead of aborting the
                        // entire highscore load.
                        LOGGER.error("Skipping malformed highscore row", e);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void addHighscoreData(WiredHighscoreDataEntry entry) {
        this.data
                .computeIfAbsent(entry.getItemId(), k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);

        this.write(() -> {
            try (Connection connection = connection()) {
                insert(connection, entry);
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    public List<WiredHighscoreRow> getHighscoreRowsForItem(
            int itemId, WiredHighscoreClearType clearType, WiredHighscoreScoreType scoreType) {
        if (!this.data.containsKey(itemId)) return null;

        List<WiredHighscoreDataEntry> list = this.data.get(itemId);
        if (list == null) return null;

        List<WiredHighscoreDataEntry> copy;
        synchronized (list) {
            copy = new ArrayList<>(list);
        }

        Stream<WiredHighscoreRow> highscores = copy.stream()
                .filter(entry -> this.timeMatchesEntry(entry, clearType)
                        && (scoreType != WiredHighscoreScoreType.MOSTWIN || entry.isWin()))
                .map(entry -> new WiredHighscoreRow(
                        entry.getUserIds().stream()
                                .map(id -> Emulator.getGameEnvironment()
                                        .getHabboManager()
                                        .getCachedUsername(id))
                                .collect(Collectors.toList()),
                        entry.getScore()));

        if (scoreType == WiredHighscoreScoreType.CLASSIC) {
            return highscores.sorted(WiredHighscoreRow::compareTo).collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.PERTEAM) {
            return highscores.collect(Collectors.groupingBy(h -> h.getUsers().hashCode())).entrySet().stream()
                    .map(e -> e.getValue().stream()
                            .sorted(WiredHighscoreRow::compareTo)
                            .collect(Collectors.toList())
                            .get(0))
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.MOSTWIN) {
            return highscores.collect(Collectors.groupingBy(h -> h.getUsers().hashCode())).entrySet().stream()
                    .map(e -> new WiredHighscoreRow(
                            e.getValue().get(0).getUsers(), e.getValue().size()))
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.LONGESTTIME) {
            return bestPerTeam(highscores, true);
        }

        // The fastest time is the same shape read the other way round: keep each team's smallest
        // score and put the smallest first, or the board would crown whoever was slowest.
        if (scoreType == WiredHighscoreScoreType.FASTESTTIME) {
            return bestPerTeam(highscores, false);
        }

        return null;
    }

    private static List<WiredHighscoreRow> bestPerTeam(Stream<WiredHighscoreRow> highscores, boolean longest) {
        Comparator<WiredHighscoreRow> byValue = Comparator.comparingInt(WiredHighscoreRow::getValue);

        return highscores.collect(Collectors.groupingBy(h -> h.getUsers().hashCode())).entrySet().stream()
                .map(e -> (longest
                                ? e.getValue().stream().max(byValue)
                                : e.getValue().stream().min(byValue))
                        .orElse(null))
                .filter(Objects::nonNull)
                .sorted(longest ? byValue.reversed() : byValue)
                .collect(Collectors.toList());
    }

    private boolean timeMatchesEntry(WiredHighscoreDataEntry entry, WiredHighscoreClearType timeType) {
        return isInCurrentPeriod(entry.getTimestamp(), timeType, HotelDateTimeUtil.localDateTimeNow(), firstDayOfWeek);
    }

    /**
     * Whether a score made at {@code timestamp} counts on a board cleared every {@code timeType}: from the first
     * second of the current period up to, not including, the first second of the next one. The bounds used to be
     * exclusive on both ends, so a score made at midnight or in the last second of the period counted nowhere.
     */
    static boolean isInCurrentPeriod(
            long timestamp, WiredHighscoreClearType timeType, LocalDateTime now, DayOfWeek firstDayOfWeek) {
        LocalDateTime today = now.with(LocalTime.MIDNIGHT);
        LocalDateTime start;
        LocalDateTime next;
        switch (timeType) {
            case DAILY -> {
                start = today;
                next = start.plusDays(1);
            }
            case WEEKLY -> {
                start = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek));
                next = start.plusWeeks(1);
            }
            case MONTHLY -> {
                start = today.with(TemporalAdjusters.firstDayOfMonth());
                next = start.plusMonths(1);
            }
            case ALLTIME -> {
                return true;
            }
            default -> {
                return false;
            }
        }

        return timestamp >= HotelDateTimeUtil.toEpochSecond(start) && timestamp < HotelDateTimeUtil.toEpochSecond(next);
    }

    public Map<Integer, List<WiredHighscoreDataEntry>> getData() {
        return this.data;
    }

    public List<WiredHighscoreDataEntry> getEntriesForItemId(int itemId) {
        return this.data.get(itemId);
    }

    /**
     * Replace everything this board holds, in memory and on disk.
     *
     * <p>Nothing ever deleted from {@code items_highscore_data}: the table was only read at boot and
     * appended to, so the reset box emptied the board in memory and every score came back at the next
     * start. The write is threaded like the insert, and the memory swap happens first so a board that
     * is read in the same tick already shows the new state.
     */
    public void setEntriesForItemId(int itemId, List<WiredHighscoreDataEntry> entries) {
        List<WiredHighscoreDataEntry> stored = Collections.synchronizedList(new ArrayList<>(entries));
        this.data.put(itemId, stored);

        this.write(() -> {
            try (Connection connection = connection()) {
                try (PreparedStatement delete =
                        connection.prepareStatement("DELETE FROM `items_highscore_data` WHERE `item_id` = ?")) {
                    delete.setInt(1, itemId);
                    delete.execute();
                }

                if (stored.isEmpty()) {
                    return;
                }

                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO `items_highscore_data` (`item_id`, `user_ids`, `score`, `is_win`, `timestamp`) VALUES (?, ?, ?, ?, ?)")) {
                    for (WiredHighscoreDataEntry entry : stored) {
                        insert.setInt(1, entry.getItemId());
                        insert.setString(2, joinUserIds(entry.getUserIds()));
                        insert.setInt(3, entry.getScore());
                        insert.setInt(4, entry.isWin() ? 1 : 0);
                        insert.setInt(5, entry.getTimestamp());
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    static String joinUserIds(List<Integer> userIds) {
        return String.join(",", userIds.stream().map(Object::toString).collect(Collectors.toList()));
    }
}
