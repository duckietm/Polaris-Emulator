package com.eu.habbo.habbohotel.wheel;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WheelManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(WheelManager.class);
    private static final int RECENT_KEEP = 50;
    private static final int SECONDS_PER_DAY = 86400;

    public static final Set<String> VALID_PRIZE_TYPES = Set.of("credits", "points", "spin", "item", "badge", "nothing");
    public static final int MAX_PRIZES_PER_SAVE = 64;
    public static final int MAX_STRING_LEN = 64;
    public static final int MAX_PRIZE_AMOUNT = 1_000_000;
    public static final int MAX_ITEM_QUANTITY = 100;
    public static final int MAX_WEIGHT = 1_000_000;
    public static final int MAX_EXTRA_SPINS = 10_000;
    private static final long MIN_SPIN_INTERVAL_MS = 1500L;

    /**
     * The prizes and their total weight, swapped in whole by a reload. Clearing and refilling one shared list while a
     * spin was picking from it threw (index or concurrent modification) after the spin had already been used.
     */
    private record PrizeTable(List<WheelPrize> prizes, int totalWeight) {}

    private volatile PrizeTable prizeTable = new PrizeTable(List.of(), 0);
    private int freeSpinsPerDay = 1;
    private int spinCost = 50;
    private int spinCostType = 5;
    private ConfigurationManager config;

    private final ConcurrentHashMap<Integer, Long> lastSpinAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, WheelUserState> userStateCache = new ConcurrentHashMap<>();
    // One lock per user: the methods were synchronized on the manager, so every player waited on the database writes
    // of every other player's spin.
    private final ConcurrentHashMap<Integer, Object> userLocks = new ConcurrentHashMap<>();
    private final java.util.concurrent.CopyOnWriteArrayList<WheelRecentWin> recentWinsCache =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public WheelManager() {
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info("Wheel Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void reload() {
        this.loadSettings();
        this.loadPrizes();
        this.loadRecentWins();
    }

    private void loadSettings() {
        ConfigurationManager configuration = Emulator.getConfig();

        this.config = configuration;
        this.freeSpinsPerDay = configuration.getInt("wheel.free_spins_per_day", 1);
        this.spinCost = configuration.getInt("wheel.spin_cost", 50);
        this.spinCostType = configuration.getInt("wheel.spin_cost_type", 5);
    }

    /**
     * {@code wheel.enabled}: 0 turns the fortune wheel off, players can no longer open it, spin or buy spins. Read on
     * every request, so {@code :update_config} switches it at once.
     */
    public boolean isEnabled() {
        return this.config == null || this.config.getBoolean("wheel.enabled", true);
    }

    private void loadPrizes() {
        List<WheelPrize> loaded = new ArrayList<>();
        int weight = 0;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM wheel_prizes WHERE enabled = 1 ORDER BY sort_order ASC, id ASC");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                WheelPrize prize = new WheelPrize(set);
                loaded.add(prize);
                weight += prize.weight;
            }
        } catch (SQLException e) {
            // The prizes already loaded stay in use rather than an empty wheel.
            LOGGER.error("Failed to load fortune wheel prizes", e);
            return;
        }

        this.prizeTable = new PrizeTable(Collections.unmodifiableList(loaded), weight);
    }

    public List<WheelPrize> getPrizes() {
        return this.prizeTable.prizes();
    }

    public int getSpinCost() {
        return this.spinCost;
    }

    public int getSpinCostType() {
        return this.spinCostType;
    }

    private int today() {
        return Emulator.getIntUnixTimestamp() / SECONDS_PER_DAY;
    }

    private Object lockFor(int userId) {
        return this.userLocks.computeIfAbsent(userId, id -> new Object());
    }

    /** Drops what is kept for a user who logged out; the state is read again from the database on the next visit. */
    public void forgetUser(int userId) {
        synchronized (this.lockFor(userId)) {
            this.lastSpinAt.remove(userId);
            this.userStateCache.remove(userId);
        }
        this.userLocks.remove(userId);
    }

    public WheelUserState getUserState(int userId) {
        synchronized (this.lockFor(userId)) {
            return this.getUserStateLocked(userId);
        }
    }

    private WheelUserState getUserStateLocked(int userId) {
        int today = this.today();
        WheelUserState cached = this.userStateCache.get(userId);

        if (cached != null) {
            if (cached.lastReset != today) {
                cached.freeSpins = this.freeSpinsPerDay;
                cached.lastReset = today;
                this.persistUserState(userId, cached);
            }
            return cached;
        }

        WheelUserState state = new WheelUserState();
        boolean exists = false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT free_spins, extra_spins, last_reset FROM wheel_user_state WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    state.freeSpins = set.getInt("free_spins");
                    state.extraSpins = set.getInt("extra_spins");
                    state.lastReset = set.getInt("last_reset");
                    exists = true;
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to read wheel state for user {}", userId, e);
        }

        if (!exists) {
            state.freeSpins = this.freeSpinsPerDay;
            state.extraSpins = 0;
            state.lastReset = today;
            this.persistUserState(userId, state);
        } else if (state.lastReset != today) {
            state.freeSpins = this.freeSpinsPerDay;
            state.lastReset = today;
            this.persistUserState(userId, state);
        }

        this.userStateCache.put(userId, state);
        return state;
    }

    private void persistUserState(int userId, WheelUserState state) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO wheel_user_state (user_id, free_spins, extra_spins, last_reset) VALUES (?, ?, ?, ?) "
                                + "ON DUPLICATE KEY UPDATE free_spins = VALUES(free_spins), extra_spins = VALUES(extra_spins), last_reset = VALUES(last_reset)")) {
            statement.setInt(1, userId);
            statement.setInt(2, state.freeSpins);
            statement.setInt(3, state.extraSpins);
            statement.setInt(4, state.lastReset);
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to persist wheel state for user {}", userId, e);
        }
    }

    public WheelPrize spin(Habbo habbo) {
        synchronized (this.lockFor(habbo.getHabboInfo().getId())) {
            return this.spinLocked(habbo);
        }
    }

    private WheelPrize spinLocked(Habbo habbo) {
        int userId = habbo.getHabboInfo().getId();
        long now = System.currentTimeMillis();
        Long last = this.lastSpinAt.get(userId);
        if (last != null && (now - last) < MIN_SPIN_INTERVAL_MS) return null;
        this.lastSpinAt.put(userId, now);

        WheelUserState state = this.getUserState(userId);

        boolean usedFree;
        if (state.freeSpins > 0) {
            state.freeSpins--;
            usedFree = true;
        } else if (state.extraSpins > 0) {
            state.extraSpins--;
            usedFree = false;
        } else {
            return null;
        }

        WheelPrize prize = this.pickWeighted();
        if (prize == null) {
            if (usedFree) state.freeSpins++;
            else state.extraSpins++;
            return null;
        }

        this.giveReward(habbo, prize, state);
        this.persistUserState(userId, state);
        this.recordWin(habbo, prize);

        return prize;
    }

    private WheelPrize pickWeighted() {
        PrizeTable table = this.prizeTable;
        if (table.prizes().isEmpty() || table.totalWeight() <= 0) return null;

        int roll = ThreadLocalRandom.current().nextInt(table.totalWeight());
        int acc = 0;
        for (WheelPrize prize : table.prizes()) {
            acc += prize.weight;
            if (roll < acc) return prize;
        }
        return table.prizes().get(table.prizes().size() - 1);
    }

    private void giveReward(Habbo habbo, WheelPrize prize, WheelUserState state) {
        int amount = Math.max(0, Math.min(prize.amount, MAX_PRIZE_AMOUNT));

        switch (prize.type) {
            case "credits":
                if (amount > 0) habbo.giveCredits(amount);
                break;
            case "points":
                if (amount > 0) habbo.givePoints(prize.pointsType, amount);
                break;
            case "spin":
                int room = Math.max(0, MAX_EXTRA_SPINS - state.extraSpins);
                state.extraSpins += Math.min(amount, room);
                break;
            case "item":
                this.giveItem(habbo, prize, Math.min(amount, MAX_ITEM_QUANTITY));
                break;
            case "badge":
                if (prize.value != null && !prize.value.isEmpty()) {
                    habbo.addBadge(prize.value, "Fortune Wheel");
                }
                break;
            case "nothing":
            default:
                break;
        }
    }

    private void giveItem(Habbo habbo, WheelPrize prize, int quantity) {
        if (quantity <= 0 || prize.value == null) return;

        int baseId;
        try {
            baseId = Integer.parseInt(prize.value.trim());
        } catch (NumberFormatException e) {
            return;
        }

        Item base = Emulator.getGameEnvironment().getItemManager().getItem(baseId);
        if (base == null) return;

        Set<HabboItem> items = new HashSet<>();
        for (int i = 0; i < quantity; i++) {
            HabboItem item = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(habbo.getHabboInfo().getId(), base, 0, 0, "");
            if (item != null) items.add(item);
        }

        if (!items.isEmpty()) {
            habbo.addFurniture(items);
        }
    }

    private void recordWin(Habbo habbo, WheelPrize prize) {
        WheelRecentWin win = new WheelRecentWin(
                habbo.getHabboInfo().getUsername(), habbo.getHabboInfo().getLook(), prize.label);

        this.recentWinsCache.add(0, win);
        while (this.recentWinsCache.size() > RECENT_KEEP) {
            this.recentWinsCache.remove(this.recentWinsCache.size() - 1);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO wheel_recent_wins (user_id, username, look, prize_label, won_at) VALUES (?, ?, ?, ?, ?)")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                statement.setString(2, habbo.getHabboInfo().getUsername());
                statement.setString(3, habbo.getHabboInfo().getLook());
                statement.setString(4, prize.label);
                statement.setInt(5, Emulator.getIntUnixTimestamp());
                statement.executeUpdate();
            }

            try (PreparedStatement trim = connection.prepareStatement(
                    "DELETE FROM wheel_recent_wins WHERE id < (SELECT id FROM (SELECT id FROM wheel_recent_wins ORDER BY id DESC LIMIT 1 OFFSET ?) t)")) {
                trim.setInt(1, RECENT_KEEP - 1);
                trim.executeUpdate();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to record wheel win", e);
        }
    }

    public List<WheelRecentWin> getRecentWins(int limit) {
        if (limit <= 0) return new ArrayList<>();
        int size = this.recentWinsCache.size();
        if (size == 0) return new ArrayList<>();
        int take = Math.min(limit, size);
        return new ArrayList<>(this.recentWinsCache.subList(0, take));
    }

    private void loadRecentWins() {
        this.recentWinsCache.clear();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT username, look, prize_label FROM wheel_recent_wins ORDER BY id DESC LIMIT ?")) {
            statement.setInt(1, RECENT_KEEP);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.recentWinsCache.add(new WheelRecentWin(
                            set.getString("username"), set.getString("look"), set.getString("prize_label")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load wheel recent wins", e);
        }
    }

    public boolean buySpin(Habbo habbo) {
        synchronized (this.lockFor(habbo.getHabboInfo().getId())) {
            return this.buySpinLocked(habbo);
        }
    }

    private boolean buySpinLocked(Habbo habbo) {
        if (this.spinCost <= 0) return false;

        int userId = habbo.getHabboInfo().getId();
        WheelUserState state = this.getUserState(userId);
        if (state.extraSpins >= MAX_EXTRA_SPINS) return false;

        // The spin is granted only for a payment that went through: the void give* calls did not say when a plugin
        // or the ledger refused the debit, and the spin was added anyway.
        if (this.spinCost > 0) {
            if (this.spinCostType == -1) {
                if (habbo.getHabboInfo().getCredits() < this.spinCost) return false;
                if (!habbo.tryTakeCredits(this.spinCost)) return false;
            } else {
                if (habbo.getHabboInfo().getCurrencyAmount(this.spinCostType) < this.spinCost) return false;
                if (!habbo.tryTakePoints(this.spinCostType, this.spinCost)) return false;
            }
        }

        state.extraSpins++;
        this.persistUserState(userId, state);
        return true;
    }

    /**
     * Persists a single prize. An {@code id <= 0} inserts a brand-new prize and
     * returns its generated id; a positive id updates the existing row (and
     * re-enables it, so a previously soft-deleted prize can be brought back).
     * {@code sortOrder} reflects the prize's position in the editor so the
     * wheel layout matches what the admin sees. Returns the effective row id,
     * or {@code 0} if the write failed.
     */
    public int savePrize(
            int id, String type, String value, int amount, int pointsType, int weight, String label, int sortOrder) {
        String safeType = (type != null && VALID_PRIZE_TYPES.contains(type)) ? type : "nothing";
        String safeValue = truncate(value, MAX_STRING_LEN);
        String safeLabel = truncate(label, MAX_STRING_LEN);
        int safeAmount = clamp(amount, 0, MAX_PRIZE_AMOUNT);
        int safeWeight = clamp(weight, 0, MAX_WEIGHT);
        int safeSort = clamp(sortOrder, 0, MAX_PRIZES_PER_SAVE);

        if (id > 0) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE wheel_prizes SET type = ?, value = ?, amount = ?, points_type = ?, weight = ?, label = ?, sort_order = ?, enabled = 1 WHERE id = ?")) {
                statement.setString(1, safeType);
                statement.setString(2, safeValue);
                statement.setInt(3, safeAmount);
                statement.setInt(4, pointsType);
                statement.setInt(5, safeWeight);
                statement.setString(6, safeLabel);
                statement.setInt(7, safeSort);
                statement.setInt(8, id);
                statement.executeUpdate();
                return id;
            } catch (SQLException e) {
                LOGGER.error("Failed to save wheel prize {}", id, e);
                return 0;
            }
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO wheel_prizes (type, value, amount, points_type, weight, label, enabled, sort_order) VALUES (?, ?, ?, ?, ?, ?, 1, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, safeType);
            statement.setString(2, safeValue);
            statement.setInt(3, safeAmount);
            statement.setInt(4, pointsType);
            statement.setInt(5, safeWeight);
            statement.setString(6, safeLabel);
            statement.setInt(7, safeSort);
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to insert wheel prize", e);
        }
        return 0;
    }

    /**
     * Soft-deletes every enabled prize whose id is not in {@code keptIds} by
     * setting {@code enabled = 0}. This is intentionally non-destructive: rows
     * stay in the table (so historical references and re-enabling remain
     * possible) but {@link #loadPrizes()} only ever loads {@code enabled = 1}.
     * An empty set disables all prizes.
     */
    public void disablePrizesNotIn(Set<Integer> keptIds) {
        if (keptIds == null) return;

        StringBuilder sql = new StringBuilder("UPDATE wheel_prizes SET enabled = 0 WHERE enabled = 1");
        if (!keptIds.isEmpty()) {
            StringJoiner ids = new StringJoiner(",", " AND id NOT IN (", ")");
            for (Integer keptId : keptIds) {
                ids.add(Integer.toString(keptId));
            }
            sql.append(ids);
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.executeUpdate();
        } catch (SQLException e) {
            LOGGER.error("Failed to disable removed wheel prizes", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
