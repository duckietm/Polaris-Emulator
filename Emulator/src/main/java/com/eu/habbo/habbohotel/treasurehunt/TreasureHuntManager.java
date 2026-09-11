package com.eu.habbo.habbohotel.treasurehunt;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.treasurehunt.TreasureHuntFailComposer;
import com.eu.habbo.messages.outgoing.treasurehunt.TreasureHuntFirstWinnerComposer;
import com.eu.habbo.messages.outgoing.treasurehunt.TreasureHuntUpdateComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR 13 treasure hunt. A hunt is a set of furniture hidden in rooms; the
 * "find" is a click on one of those items ({@code ClickFurniEvent}), exactly
 * like the official client, which has no packet of its own for finding.
 *
 * <p>A find answers with {@code TreasureHuntUpdate} (progress, or the completed
 * flag on the last item) or with {@code TreasureHuntFail} when the player is
 * below the level the hunt asks for. The first player to complete a hunt is
 * announced to everyone online with {@code TreasureHuntFirstWinner}.
 */
public class TreasureHuntManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TreasureHuntManager.class);

    /** item id -> hunt id, so a click is a map lookup and never a query. */
    private final Map<Integer, Integer> huntByItemId = new ConcurrentHashMap<>();

    private final Map<Integer, TreasureHunt> hunts = new ConcurrentHashMap<>();

    /** hunt ids that already have a winner, so the first-winner packet is sent once. */
    private final Map<Integer, Boolean> hasWinner = new ConcurrentHashMap<>();

    public TreasureHuntManager() {
        long millis = System.currentTimeMillis();
        this.reload();
        LOGGER.info("Treasure Hunt Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public synchronized void reload() {
        Map<Integer, TreasureHunt> loadedHunts = new HashMap<>();
        Map<Integer, Integer> loadedItems = new HashMap<>();
        Map<Integer, Boolean> loadedWinners = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            Map<Integer, Integer> stepCounts = new HashMap<>();

            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT hunt_id, item_id FROM treasure_hunt_items WHERE enabled = 1");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int huntId = set.getInt("hunt_id");
                    loadedItems.put(set.getInt("item_id"), huntId);
                    stepCounts.merge(huntId, 1, Integer::sum);
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT id, code, required_level, required_level_paying, reward_badge, reward_item_id, reward_points, reward_points_type FROM treasure_hunts WHERE enabled = 1");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int id = set.getInt("id");
                    loadedHunts.put(
                            id,
                            new TreasureHunt(
                                    id,
                                    value(set.getString("code")),
                                    set.getInt("required_level"),
                                    set.getInt("required_level_paying"),
                                    value(set.getString("reward_badge")),
                                    set.getInt("reward_item_id"),
                                    set.getInt("reward_points"),
                                    set.getInt("reward_points_type"),
                                    stepCounts.getOrDefault(id, 0)));
                }
            }

            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT DISTINCT hunt_id FROM treasure_hunt_progress WHERE completed = 1");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) loadedWinners.put(set.getInt("hunt_id"), Boolean.TRUE);
            }
        } catch (SQLException e) {
            LOGGER.warn("Treasure hunts could not be loaded; the feature stays disabled.", e);
            return;
        }

        this.hunts.clear();
        this.hunts.putAll(loadedHunts);
        this.huntByItemId.clear();
        this.huntByItemId.putAll(loadedItems);
        this.hasWinner.clear();
        this.hasWinner.putAll(loadedWinners);
    }

    /** The hunt a piece of furniture belongs to, or null when it hides nothing. */
    public TreasureHunt getHuntForItem(int itemId) {
        Integer huntId = this.huntByItemId.get(itemId);
        return huntId == null ? null : this.hunts.get(huntId);
    }

    public TreasureHunt getHunt(String code) {
        if (code == null || code.isBlank()) return null;

        return this.hunts.values().stream()
                .filter(hunt -> code.equals(hunt.code()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Registers a click on a hidden item. Returns true when the click belonged to
     * a hunt, so the caller knows the click was consumed by the hunt.
     */
    public boolean onItemClicked(Habbo habbo, int itemId) {
        if (habbo == null || habbo.getClient() == null) return false;

        TreasureHunt hunt = this.getHuntForItem(itemId);
        if (hunt == null || hunt.totalSteps() <= 0) return false;

        int level = habbo.getHabboStats().getAchievementScore();
        int required = habbo.getHabboStats().hasActiveClub() ? hunt.requiredLevelPaying() : hunt.requiredLevel();

        if (level < required) {
            habbo.getClient()
                    .sendResponse(new TreasureHuntFailComposer(
                            hunt.code(), hunt.requiredLevel(), hunt.requiredLevelPaying()));
            return true;
        }

        int userId = habbo.getHabboInfo().getId();
        int stepsCompleted;
        boolean completedNow;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            if (!this.recordFind(connection, hunt.id(), userId, itemId)) {
                // Already found: repeat the current progress, like the official client
                // which keeps showing the same bubble.
                stepsCompleted = this.countFinds(connection, hunt.id(), userId);
                habbo.getClient()
                        .sendResponse(new TreasureHuntUpdateComposer(
                                hunt.code(), stepsCompleted, hunt.totalSteps(), stepsCompleted >= hunt.totalSteps()));
                return true;
            }

            stepsCompleted = this.countFinds(connection, hunt.id(), userId);
            completedNow = stepsCompleted >= hunt.totalSteps();
            this.saveProgress(connection, hunt.id(), userId, stepsCompleted, completedNow);
        } catch (SQLException e) {
            LOGGER.error("Could not register a treasure hunt find for user {}", userId, e);
            return true;
        }

        habbo.getClient()
                .sendResponse(
                        new TreasureHuntUpdateComposer(hunt.code(), stepsCompleted, hunt.totalSteps(), completedNow));

        if (completedNow) {
            this.awardHunt(habbo, hunt);
            this.announceFirstWinner(habbo, hunt);
        }

        return true;
    }

    private boolean recordFind(Connection connection, int huntId, int userId, int itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT IGNORE INTO treasure_hunt_finds (hunt_id, user_id, item_id, found_at) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, huntId);
            statement.setInt(2, userId);
            statement.setInt(3, itemId);
            statement.setInt(4, Emulator.getIntUnixTimestamp());
            return statement.executeUpdate() == 1;
        }
    }

    private int countFinds(Connection connection, int huntId, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) AS total FROM treasure_hunt_finds WHERE hunt_id = ? AND user_id = ?")) {
            statement.setInt(1, huntId);
            statement.setInt(2, userId);

            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt("total") : 0;
            }
        }
    }

    private void saveProgress(Connection connection, int huntId, int userId, int steps, boolean completed)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO treasure_hunt_progress (hunt_id, user_id, steps_completed, completed, completed_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE steps_completed = VALUES(steps_completed),
                    completed = VALUES(completed),
                    completed_at = IF(treasure_hunt_progress.completed_at > 0, treasure_hunt_progress.completed_at, VALUES(completed_at))
                """)) {
            statement.setInt(1, huntId);
            statement.setInt(2, userId);
            statement.setInt(3, steps);
            statement.setInt(4, completed ? 1 : 0);
            statement.setInt(5, completed ? Emulator.getIntUnixTimestamp() : 0);
            statement.executeUpdate();
        }
    }

    private void awardHunt(Habbo habbo, TreasureHunt hunt) {
        if (!hunt.rewardBadge().isBlank()) habbo.addBadge(hunt.rewardBadge(), "Treasure Hunt");

        if (hunt.rewardPoints() > 0) habbo.givePoints(hunt.rewardPointsType(), hunt.rewardPoints());

        if (hunt.rewardItemId() > 0) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(hunt.rewardItemId());

            if (baseItem != null) {
                HabboItem reward = Emulator.getGameEnvironment()
                        .getItemManager()
                        .createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");

                if (reward != null) {
                    habbo.getInventory().getItemsComponent().addItem(reward);
                    habbo.getClient().sendResponse(new AddHabboItemComposer(reward));
                    habbo.getClient().sendResponse(new InventoryRefreshComposer());
                }
            }
        }
    }

    private void announceFirstWinner(Habbo habbo, TreasureHunt hunt) {
        if (this.hasWinner.putIfAbsent(hunt.id(), Boolean.TRUE) != null) return;

        TreasureHuntFirstWinnerComposer composer = new TreasureHuntFirstWinnerComposer(
                hunt.code(),
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getUsername(),
                habbo.getHabboInfo().getLook(),
                habbo.getHabboInfo().getGender().name());

        Collection<Habbo> online = Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .values();

        for (Habbo online0 : online) {
            if (online0 == null || online0.getClient() == null) continue;

            online0.getClient().sendResponse(composer);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public void dispose() {
        this.hunts.clear();
        this.huntByItemId.clear();
        this.hasWinner.clear();
        LOGGER.info("Treasure Hunt Manager -> Disposed!");
    }
}
