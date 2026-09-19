package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.quests.RewardTrackClaimResultComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackPremiumPurchaseResultComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackProgressComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTrackTextsComposer;
import com.eu.habbo.messages.outgoing.quests.RewardTracksComposer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The AIR 13 reward track: tasks pay points at each level, points unlock prizes, an optional premium
 * pass boosts the points and opens the premium prizes.
 */
public class RewardTrackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardTrackManager.class);

    /** Result codes: 0 is success, the client localizes any other value as reward_track.*.notification.fail.&lt;code&gt;. */
    public static final int RESULT_OK = 0;

    public static final int RESULT_UNKNOWN = 1;
    public static final int RESULT_NOT_ENOUGH_POINTS = 2;
    public static final int RESULT_PREMIUM_REQUIRED = 3;
    public static final int RESULT_ALREADY_CLAIMED = 4;
    public static final int RESULT_NOT_ENOUGH_CURRENCY = 5;
    public static final int RESULT_ALREADY_PREMIUM = 6;

    private final Map<String, RewardTrack> tracks = new LinkedHashMap<>();
    private final Map<Integer, Map<String, UserRewardTrackState>> users = new ConcurrentHashMap<>();

    /** The localization texts of every track, by track id then key suffix, read with the tracks. */
    private final Map<String, Map<String, String>> texts = new ConcurrentHashMap<>();

    private final boolean persistent;

    public RewardTrackManager() {
        this(true);
        this.reload();
    }

    protected RewardTrackManager(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Reads the tracks again and drops every cached user state, so the next request loads it from the
     * database: every change to a state is written through, nothing in the cache is newer than the rows.
     */
    public synchronized void reload() {
        this.tracks.clear();
        this.users.clear();
        this.texts.clear();
        if (!this.persistent) {
            return;
        }
        for (LoadedTrack loaded : loadFromDatabase(true)) {
            this.register(loaded.track());
        }
        this.texts.putAll(RewardTrackAdmin.loadTexts());
        LOGGER.info("Reward Track Manager -> Loaded! ({} tracks)", this.tracks.size());
    }

    /** Reloads the tracks and sends the fresh list to every client with a logged-in user. */
    public void reloadAndBroadcast() {
        this.reload();
        for (GameClient client :
                Emulator.getGameServer().getGameClientManager().getSessions().values()) {
            if (client.getHabbo() != null) {
                this.sendRewardTracks(client.getHabbo(), true);
            }
        }
    }

    /** A track row as stored, with the flag the running hotel filters on. */
    public record LoadedTrack(RewardTrack track, boolean enabled) {}

    /**
     * Reads the tracks with their tasks, levels and prizes. The hotel loads only the enabled ones;
     * the staff editor reads them all.
     */
    public static List<LoadedTrack> loadFromDatabase(boolean onlyEnabled) {
        Map<String, LoadedTrack> loaded = new LinkedHashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM reward_tracks"
                            + (onlyEnabled ? " WHERE enabled = 1" : "") + " ORDER BY sort_order, id");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    RewardTrack track = new RewardTrack(
                            set.getString("id"),
                            set.getString("theme"),
                            set.getInt("sort_order"),
                            set.getInt("starts_at"),
                            set.getInt("ends_at"),
                            set.getBoolean("has_premium"),
                            set.getDouble("premium_task_points_boost"),
                            set.getInt("premium_instant_points"),
                            set.getInt("premium_cost_diamonds"),
                            set.getInt("premium_cost_credits"));
                    loaded.put(track.getId(), new LoadedTrack(track, set.getBoolean("enabled")));
                }
            }
            try (PreparedStatement statement =
                            connection.prepareStatement("SELECT * FROM reward_track_tasks ORDER BY sort_order, id");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    LoadedTrack track = loaded.get(set.getString("track_id"));
                    if (track == null) {
                        continue;
                    }
                    RewardTrack.Task task = new RewardTrack.Task(
                            set.getString("id"),
                            set.getString("action_type"),
                            set.getString("parameter"),
                            set.getBoolean("premium"),
                            set.getInt("sort_order"));
                    if (task.getGoalType() == null) {
                        LOGGER.warn(
                                "Reward track task {}/{} has an unknown action type, skipped",
                                track.track().getId(),
                                task.getId());
                        continue;
                    }
                    track.track().addTask(task);
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM reward_track_task_levels ORDER BY track_id, task_id, level");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    LoadedTrack track = loaded.get(set.getString("track_id"));
                    RewardTrack.Task task = track == null ? null : track.track().getTask(set.getString("task_id"));
                    if (task != null) {
                        task.addLevel(new RewardTrack.Level(
                                set.getInt("required_count"), set.getInt("points_reward"), set.getBoolean("premium")));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT * FROM reward_track_prizes ORDER BY required_points, sort_order, id");
                    ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    LoadedTrack track = loaded.get(set.getString("track_id"));
                    if (track != null) {
                        track.track()
                                .addPrize(new RewardTrack.Prize(
                                        set.getString("id"),
                                        set.getInt("required_points"),
                                        set.getInt("product_item_type_id"),
                                        set.getString("reward_type"),
                                        set.getString("extra_params"),
                                        set.getInt("reward_amount"),
                                        set.getBoolean("premium"),
                                        set.getInt("sort_order")));
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the reward tracks", exception);
        }
        return new ArrayList<>(loaded.values());
    }

    public synchronized void register(RewardTrack track) {
        this.tracks.put(track.getId(), track);
    }

    public RewardTrack getTrack(String id) {
        return this.tracks.get(id);
    }

    /** The tracks running now, in display order. */
    public List<RewardTrack> activeTracks() {
        int now = Emulator.getIntUnixTimestamp();
        List<RewardTrack> active = new ArrayList<>();
        for (RewardTrack track : this.tracks.values()) {
            if (track.isActive(now)) {
                active.add(track);
            }
        }
        active.sort(Comparator.comparingInt(RewardTrack::getSortOrder).thenComparing(RewardTrack::getId));
        return Collections.unmodifiableList(active);
    }

    // ------------------------------------------------------------------ user state

    public UserRewardTrackState stateFor(Habbo habbo, RewardTrack track) {
        return this.stateFor(habbo.getHabboInfo().getId(), track);
    }

    public UserRewardTrackState stateFor(int userId, RewardTrack track) {
        Map<String, UserRewardTrackState> byTrack = this.users.computeIfAbsent(userId, id -> new ConcurrentHashMap<>());
        return byTrack.computeIfAbsent(track.getId(), trackId -> this.load(userId, trackId));
    }

    public void unload(int userId) {
        this.users.remove(userId);
    }

    private UserRewardTrackState load(int userId, String trackId) {
        UserRewardTrackState state = new UserRewardTrackState(trackId, 0, false);
        if (!this.persistent) {
            return state;
        }
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT points, premium FROM users_reward_tracks WHERE user_id = ? AND track_id = ?")) {
                statement.setInt(1, userId);
                statement.setString(2, trackId);
                try (ResultSet set = statement.executeQuery()) {
                    if (set.next()) {
                        state = new UserRewardTrackState(trackId, set.getInt("points"), set.getBoolean("premium"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT task_id, progress_count FROM users_reward_track_tasks WHERE user_id = ? AND track_id = ?")) {
                statement.setInt(1, userId);
                statement.setString(2, trackId);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        state.setProgress(set.getString("task_id"), set.getInt("progress_count"));
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT prize_id FROM users_reward_track_prizes WHERE user_id = ? AND track_id = ?")) {
                statement.setInt(1, userId);
                statement.setString(2, trackId);
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        state.markClaimed(set.getString("prize_id"));
                    }
                }
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load reward track {} of user {}", trackId, userId, exception);
        }
        return state;
    }

    protected void saveTrack(int userId, UserRewardTrackState state) {
        if (!this.persistent) {
            return;
        }
        int points = state.getPoints();
        boolean premium = state.isPremium();
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_reward_tracks (user_id, track_id, points, premium) VALUES (?, ?, ?, ?)"
                                    + " ON DUPLICATE KEY UPDATE points = VALUES(points), premium = VALUES(premium)")) {
                statement.setInt(1, userId);
                statement.setString(2, state.getTrackId());
                statement.setInt(3, points);
                statement.setBoolean(4, premium);
                statement.execute();
            } catch (SQLException exception) {
                LOGGER.error("Could not save reward track {} of user {}", state.getTrackId(), userId, exception);
            }
        });
    }

    protected void saveTaskProgress(int userId, String trackId, String taskId, int count) {
        if (!this.persistent) {
            return;
        }
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_reward_track_tasks (user_id, track_id, task_id, progress_count)"
                                    + " VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE progress_count = VALUES(progress_count)")) {
                statement.setInt(1, userId);
                statement.setString(2, trackId);
                statement.setString(3, taskId);
                statement.setInt(4, count);
                statement.execute();
            } catch (SQLException exception) {
                LOGGER.error("Could not save reward track task {}/{} of user {}", trackId, taskId, userId, exception);
            }
        });
    }

    protected void saveClaim(int userId, String trackId, String prizeId) {
        if (!this.persistent) {
            return;
        }
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT IGNORE INTO users_reward_track_prizes (user_id, track_id, prize_id, claimed_at)"
                                    + " VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, userId);
                statement.setString(2, trackId);
                statement.setString(3, prizeId);
                statement.setInt(4, Emulator.getIntUnixTimestamp());
                statement.execute();
            } catch (SQLException exception) {
                LOGGER.error("Could not save reward track claim {}/{} of user {}", trackId, prizeId, userId, exception);
            }
        });
    }

    // ------------------------------------------------------------------ wire

    public RewardTracksComposer.Track toWire(RewardTrack track, UserRewardTrackState state) {
        List<RewardTracksComposer.Task> tasks = new ArrayList<>();
        for (RewardTrack.Task task : track.getTasks()) {
            List<RewardTracksComposer.Level> levels = new ArrayList<>();
            for (RewardTrack.Level level : task.getLevels()) {
                levels.add(
                        new RewardTracksComposer.Level(level.requiredCount(), level.pointsReward(), level.premium()));
            }
            tasks.add(new RewardTracksComposer.Task(
                    task.getId(),
                    task.getActionType(),
                    task.getParameter(),
                    state.progressOf(task.getId()),
                    task.isPremium(),
                    levels));
        }
        List<RewardTracksComposer.Prize> prizes = new ArrayList<>();
        boolean complete = true;
        boolean premiumComplete = true;
        for (RewardTrack.Prize prize : track.getPrizes()) {
            boolean claimed = state.isClaimed(prize.getId());
            if (!claimed) {
                if (prize.isPremium()) {
                    premiumComplete = false;
                } else {
                    complete = false;
                }
            }
            // A furni prize travels with its sprite id and "<s|i>:<name>", what the client needs to draw it.
            int productItemTypeId = prize.getProductItemTypeId();
            String extraParams = prize.getExtraParams();
            if (QuestRewards.TYPE_FURNI.equalsIgnoreCase(prize.getRewardType())
                    && Emulator.getGameEnvironment() != null) {
                Item item = Emulator.getGameEnvironment().getItemManager().getItem(prize.getExtraParams());
                if (item != null) {
                    productItemTypeId = item.getSpriteId();
                    extraParams = item.getType().code.toLowerCase() + ":" + item.getName();
                }
            }
            prizes.add(new RewardTracksComposer.Prize(
                    prize.getId(),
                    prize.getRequiredPoints(),
                    productItemTypeId,
                    prize.getRewardType(),
                    extraParams,
                    prize.getRewardAmount(),
                    prize.isPremium(),
                    state.isPrizeAvailable(prize),
                    claimed));
        }
        return new RewardTracksComposer.Track(
                track.getId(),
                track.getTheme(),
                state.getPoints(),
                track.hasPremium(),
                track.getPremiumTaskPointsBoost(),
                track.getPremiumInstantPoints(),
                track.getPremiumCostDiamonds(),
                track.getPremiumCostCredits(),
                state.isPremium(),
                complete,
                !track.hasPremium() || (complete && premiumComplete),
                tasks,
                prizes);
    }

    public RewardTracksComposer rewardTracks(Habbo habbo, boolean reload) {
        List<RewardTracksComposer.Track> wire = new ArrayList<>();
        for (RewardTrack track : this.activeTracks()) {
            wire.add(this.toWire(track, this.stateFor(habbo, track)));
        }
        return new RewardTracksComposer(this.tracks.isEmpty(), wire, reload);
    }

    public void sendRewardTracks(Habbo habbo, boolean reload) {
        habbo.getClient().sendResponse(new RewardTrackTextsComposer(this.activeTexts()));
        habbo.getClient().sendResponse(this.rewardTracks(habbo, reload));
    }

    /** The texts of the active tracks as full localization keys, "reward_track.&lt;track&gt;.&lt;key&gt;". */
    public Map<String, String> activeTexts() {
        Map<String, String> full = new LinkedHashMap<>();
        for (RewardTrack track : this.activeTracks()) {
            for (Map.Entry<String, String> entry :
                    this.texts.getOrDefault(track.getId(), Map.of()).entrySet()) {
                full.put("reward_track." + track.getId() + "." + entry.getKey(), entry.getValue());
            }
        }
        return full;
    }

    // ------------------------------------------------------------------ actions

    /**
     * Staff hand: adds (or, negative, removes) points on a track; the total never goes below zero.
     * Returns the points the user has afterwards. The user's window follows when they are online.
     */
    public int adjustPoints(Habbo habbo, RewardTrack track, int delta) {
        UserRewardTrackState state = this.stateFor(habbo, track);
        state.addPoints(delta);
        this.saveTrack(habbo.getHabboInfo().getId(), state);
        if (habbo.getClient() != null) {
            this.sendRewardTracks(habbo, false);
        }
        return state.getPoints();
    }

    public void progress(Habbo habbo, QuestGoalType goalType, int amount) {
        if (habbo == null || goalType == null || amount < 1 || this.tracks.isEmpty()) {
            return;
        }
        for (RewardTrack track : this.activeTracks()) {
            UserRewardTrackState state = this.stateFor(habbo, track);
            for (RewardTrack.Task task : track.getTasks()) {
                if (task.getGoalType() != goalType || (task.isPremium() && !state.isPremium())) {
                    continue;
                }
                int before = state.progressOf(task.getId());
                if (task.isComplete(before)) {
                    continue;
                }
                int after = before + amount;
                state.setProgress(task.getId(), after);
                int points = track.pointsFor(task, before, after, state.isPremium());
                if (points > 0) {
                    state.addPoints(points);
                    this.saveTrack(habbo.getHabboInfo().getId(), state);
                }
                this.saveTaskProgress(habbo.getHabboInfo().getId(), track.getId(), task.getId(), after);
                habbo.getClient()
                        .sendResponse(
                                new RewardTrackProgressComposer(track.getId(), task.getId(), after, state.getPoints()));
            }
        }
    }

    /** ClaimRewardTrackPrize(trackId, rewardId). */
    public int claim(Habbo habbo, String trackId, String prizeId) {
        RewardTrack track = this.tracks.get(trackId);
        RewardTrack.Prize prize = track == null ? null : track.getPrize(prizeId);
        int result;
        if (track == null || prize == null) {
            result = RESULT_UNKNOWN;
        } else {
            UserRewardTrackState state = this.stateFor(habbo, track);
            if (state.isClaimed(prizeId)) {
                result = RESULT_ALREADY_CLAIMED;
            } else if (state.isPrizeLocked(prize)) {
                result = RESULT_PREMIUM_REQUIRED;
            } else if (state.getPoints() < prize.getRequiredPoints()) {
                result = RESULT_NOT_ENOUGH_POINTS;
            } else {
                state.markClaimed(prizeId);
                this.saveClaim(habbo.getHabboInfo().getId(), trackId, prizeId);
                QuestRewards.grantTyped(habbo, prize.getRewardType(), prize.getExtraParams(), prize.getRewardAmount());
                result = RESULT_OK;
            }
        }
        habbo.getClient().sendResponse(new RewardTrackClaimResultComposer(trackId, prizeId, result));
        return result;
    }

    /** PurchaseRewardTrackPremium(trackId): pays the diamonds and/or credits, unlocks the premium tier. */
    public int purchasePremium(Habbo habbo, String trackId) {
        RewardTrack track = this.tracks.get(trackId);
        int result;
        int points = 0;
        if (track == null || !track.hasPremium()) {
            result = RESULT_UNKNOWN;
        } else {
            UserRewardTrackState state = this.stateFor(habbo, track);
            points = state.getPoints();
            if (state.isPremium()) {
                result = RESULT_ALREADY_PREMIUM;
            } else if (habbo.getHabboInfo().getCredits() < track.getPremiumCostCredits()
                    || habbo.getHabboInfo().getCurrencyAmount(QuestRewards.DIAMONDS_POINT_TYPE)
                            < track.getPremiumCostDiamonds()) {
                result = RESULT_NOT_ENOUGH_CURRENCY;
            } else {
                if (track.getPremiumCostCredits() > 0) {
                    habbo.giveCredits(-track.getPremiumCostCredits(), "reward_track.premium");
                }
                if (track.getPremiumCostDiamonds() > 0) {
                    habbo.givePoints(
                            QuestRewards.DIAMONDS_POINT_TYPE, -track.getPremiumCostDiamonds(), "reward_track.premium");
                }
                state.setPremium(true);
                state.addPoints(track.getPremiumInstantPoints());
                points = state.getPoints();
                this.saveTrack(habbo.getHabboInfo().getId(), state);
                result = RESULT_OK;
            }
        }
        habbo.getClient().sendResponse(new RewardTrackPremiumPurchaseResultComposer(trackId, result, points));
        if (result == RESULT_OK) {
            this.sendRewardTracks(habbo, false);
        }
        return result;
    }
}
