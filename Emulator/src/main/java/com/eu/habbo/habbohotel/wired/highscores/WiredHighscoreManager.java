package com.eu.habbo.habbohotel.wired.highscores;

import com.eu.habbo.Emulator;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.util.HotelDateTimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WiredHighscoreManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredHighscoreManager.class);

    private final ConcurrentHashMap<Integer, List<WiredHighscoreDataEntry>> data = new ConcurrentHashMap<>();
    
    private final static String locale = (System.getProperty("user.language") != null ? System.getProperty("user.language") : "en");
    private final static String country = (System.getProperty("user.country") != null ? System.getProperty("user.country") : "US");

    private final static DayOfWeek firstDayOfWeek = WeekFields.of(Locale.of(locale, country)).getFirstDayOfWeek();
    private final static DayOfWeek lastDayOfWeek = DayOfWeek.of(((firstDayOfWeek.getValue() + 5) % DayOfWeek.values().length) + 1);
    public static ScheduledFuture<?> midnightUpdater = null;

    public void load() {
        long millis = System.currentTimeMillis();

        this.data.clear();
        this.loadHighscoreData();

        LOGGER.info("Highscore Manager -> Loaded! ({} MS, {} items)", System.currentTimeMillis() - millis, this.data.size());
    }

    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }
        
        midnightUpdater = Emulator.getThreading().run(new WiredHighscoreMidnightUpdater(), WiredHighscoreMidnightUpdater.getNextUpdaterRun());
    }

    public void dispose() {
        if (midnightUpdater != null) {
            midnightUpdater.cancel(true);
        }

        this.data.clear();
    }

    private void loadHighscoreData() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM items_highscore_data")) {
            statement.setFetchSize(1000);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    WiredHighscoreDataEntry entry = new WiredHighscoreDataEntry(set);

                    this.data.computeIfAbsent(entry.getItemId(), k -> Collections.synchronizedList(new ArrayList<>())).add(entry);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void addHighscoreData(WiredHighscoreDataEntry entry) {
        this.data.computeIfAbsent(entry.getItemId(), k -> Collections.synchronizedList(new ArrayList<>())).add(entry);

        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("INSERT INTO `items_highscore_data` (`item_id`, `user_ids`, `score`, `is_win`, `timestamp`) VALUES (?, ?, ?, ?, ?)")) {
                statement.setInt(1, entry.getItemId());
                statement.setString(2, String.join(",", entry.getUserIds().stream().map(Object::toString).collect(Collectors.toList())));
                statement.setInt(3, entry.getScore());
                statement.setInt(4, entry.isWin() ? 1 : 0);
                statement.setInt(5, entry.getTimestamp());

                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    public List<WiredHighscoreRow> getHighscoreRowsForItem(int itemId, WiredHighscoreClearType clearType, WiredHighscoreScoreType scoreType) {
        if (!this.data.containsKey(itemId)) return null;

        List<WiredHighscoreDataEntry> list = this.data.get(itemId);
        if (list == null) return null;

        List<WiredHighscoreDataEntry> copy;
        synchronized (list) {
            copy = new ArrayList<>(list);
        }

        Stream<WiredHighscoreRow> highscores = copy.stream()
                .filter(entry -> this.timeMatchesEntry(entry, clearType) && (scoreType != WiredHighscoreScoreType.MOSTWIN || entry.isWin()))
                .map(entry -> new WiredHighscoreRow(
                        entry.getUserIds().stream()
                                .map(id -> Emulator.getGameEnvironment().getHabboManager().getCachedUsername(id))
                                .collect(Collectors.toList()),
                        entry.getScore()
                ));

        if (scoreType == WiredHighscoreScoreType.CLASSIC) {
            return highscores.sorted(WiredHighscoreRow::compareTo).collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.PERTEAM) {
            return highscores
                    .collect(Collectors.groupingBy(h -> h.getUsers().hashCode()))
                    .entrySet()
                    .stream()
                    .map(e -> e.getValue().stream()
                            .sorted(WiredHighscoreRow::compareTo)
                            .collect(Collectors.toList())
                            .get(0)
                    )
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.MOSTWIN) {
            return highscores
                    .collect(Collectors.groupingBy(h -> h.getUsers().hashCode()))
                    .entrySet()
                    .stream()
                    .map(e -> new WiredHighscoreRow(e.getValue().get(0).getUsers(), e.getValue().size()))
                    .sorted(WiredHighscoreRow::compareTo)
                    .collect(Collectors.toList());
        }

        if (scoreType == WiredHighscoreScoreType.LONGESTTIME) {
            return highscores
                    .collect(Collectors.groupingBy(h -> h.getUsers().hashCode()))
                    .entrySet()
                    .stream()
                    .map(e -> e.getValue().stream()
                            .max(Comparator.comparingInt(WiredHighscoreRow::getValue))
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(WiredHighscoreRow::getValue).reversed())
                    .collect(Collectors.toList());
        }

        return null;
    }

    private boolean timeMatchesEntry(WiredHighscoreDataEntry entry, WiredHighscoreClearType timeType) {
        switch (timeType) {
            case DAILY:
                return entry.getTimestamp() > this.getTodayStartTimestamp() && entry.getTimestamp() < this.getTodayEndTimestamp();
            case WEEKLY:
                return entry.getTimestamp() > this.getWeekStartTimestamp() && entry.getTimestamp() < this.getWeekEndTimestamp();
            case MONTHLY:
                return entry.getTimestamp() > this.getMonthStartTimestamp() && entry.getTimestamp() < this.getMonthEndTimestamp();
            case ALLTIME:
                return true;
        }

        return false;
    }

    public Map<Integer, List<WiredHighscoreDataEntry>> getData() {
        return this.data;
    }

    public List<WiredHighscoreDataEntry> getEntriesForItemId(int itemId) {
        return this.data.get(itemId);
    }

    public void setEntriesForItemId(int itemId, List<WiredHighscoreDataEntry> entries) {
        this.data.put(itemId, Collections.synchronizedList(entries));
    }

    private long getTodayStartTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT));
    }

    private long getTodayEndTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1));
    }

    private long getWeekStartTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).with(TemporalAdjusters.previousOrSame(firstDayOfWeek)));
    }

    private long getWeekEndTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1).with(TemporalAdjusters.nextOrSame(lastDayOfWeek)));
    }

    private long getMonthStartTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).with(TemporalAdjusters.firstDayOfMonth()));
    }

    private long getMonthEndTimestamp() {
        return HotelDateTimeUtil.toEpochSecond(HotelDateTimeUtil.localDateTimeNow().with(LocalTime.MIDNIGHT).plusDays(1).plusSeconds(-1).with(TemporalAdjusters.lastDayOfMonth()));
    }
}
