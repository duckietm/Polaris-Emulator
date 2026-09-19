package com.eu.habbo.habbohotel.quests;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The staff editor's writes: each save validates its input, writes the rows and leaves the reload to
 * the caller. Validation is pure so the rules can be tested without a database.
 */
public final class RewardTrackAdmin {
    private static final Logger LOGGER = LoggerFactory.getLogger(RewardTrackAdmin.class);

    /** Column width of every reward track id. */
    public static final int ID_MAX_LENGTH = 64;

    public static final int TEXT_MAX_LENGTH = 255;
    public static final int THEME_MAX_LENGTH = 64;
    public static final int MAX_LEVELS = 50;

    /** The premium boost in hundredths: at most 10x. */
    public static final int MAX_BOOST_PERCENT = 1000;

    /** Points and currency amounts stay far from int overflow. */
    public static final int MAX_AMOUNT = 1_000_000;

    public static final String ENTITY_TRACK = "track";
    public static final String ENTITY_TASK = "task";
    public static final String ENTITY_PRIZE = "prize";
    public static final String ENTITY_TEXTS = "texts";

    /** A text key is the part after "reward_track.&lt;track&gt;.": name, desc, info, task.&lt;id&gt;.name ... */
    public static final int TEXT_KEY_MAX_LENGTH = 128;

    public static final int TEXT_VALUE_MAX_LENGTH = 1000;
    public static final int MAX_TEXTS_PER_TRACK = 200;
    public static final int FURNI_SEARCH_LIMIT = 30;

    /** A furni the editor may pick as a prize: its items_base name, sprite id and floor/wall code. */
    public record FurniMatch(String name, int spriteId, String typeCode) {}

    private static final List<String> REWARD_TYPES = List.of(
            QuestRewards.TYPE_DUCKETS,
            QuestRewards.TYPE_DIAMONDS,
            QuestRewards.TYPE_CREDITS,
            QuestRewards.TYPE_BADGE,
            QuestRewards.TYPE_FURNI);

    private RewardTrackAdmin() {}

    /** The premium boost travels as an integer in hundredths: 150 is 1.5x. */
    public record TrackInput(
            String id,
            String theme,
            int sortOrder,
            int startsAt,
            int endsAt,
            boolean hasPremium,
            int premiumBoostPercent,
            int premiumInstantPoints,
            int premiumCostDiamonds,
            int premiumCostCredits,
            boolean enabled) {}

    public record TaskInput(
            String trackId,
            String id,
            String actionType,
            String parameter,
            boolean premium,
            int sortOrder,
            List<RewardTrack.Level> levels) {}

    public record PrizeInput(
            String trackId,
            String id,
            int requiredPoints,
            int productItemTypeId,
            String rewardType,
            String extraParams,
            int rewardAmount,
            boolean premium,
            int sortOrder) {}

    /** The action names the editor may pick, in the order the server declares them. */
    public static List<String> actionTypes() {
        List<String> names = new ArrayList<>();
        for (QuestGoalType type : QuestGoalType.values()) {
            names.add(type.actionType());
        }
        return names;
    }

    public static List<String> rewardTypes() {
        return REWARD_TYPES;
    }

    // ------------------------------------------------------------------ validation

    /** Returns the problem with the input, or null when it can be saved. */
    public static String validate(TrackInput input) {
        String problem = validateId(input.id(), "track id");
        if (problem != null) {
            return problem;
        }
        if (input.theme() == null || input.theme().isBlank() || input.theme().length() > THEME_MAX_LENGTH) {
            return "The theme is required (up to " + THEME_MAX_LENGTH + " characters)";
        }
        if (input.startsAt() < 0 || input.endsAt() < 0) {
            return "Start and end must be unix timestamps, 0 for none";
        }
        if (input.startsAt() > 0 && input.endsAt() > 0 && input.endsAt() <= input.startsAt()) {
            return "The end must come after the start";
        }
        if (input.premiumBoostPercent() < 0
                || input.premiumInstantPoints() < 0
                || input.premiumCostDiamonds() < 0
                || input.premiumCostCredits() < 0) {
            return "Premium values cannot be negative";
        }
        if (input.premiumBoostPercent() > MAX_BOOST_PERCENT) {
            return "The premium boost is at most " + MAX_BOOST_PERCENT + "% (10x)";
        }
        if (input.premiumInstantPoints() > MAX_AMOUNT
                || input.premiumCostDiamonds() > MAX_AMOUNT
                || input.premiumCostCredits() > MAX_AMOUNT) {
            return "Premium values are at most " + MAX_AMOUNT;
        }
        return null;
    }

    public static String validate(TaskInput input) {
        String problem = validateId(input.trackId(), "track id");
        if (problem == null) {
            problem = validateId(input.id(), "task id");
        }
        if (problem != null) {
            return problem;
        }
        if (input.actionType() == null || QuestGoalType.fromCode(input.actionType()) == null) {
            return "Unknown action type: " + input.actionType();
        }
        if (input.parameter() != null && input.parameter().length() > TEXT_MAX_LENGTH) {
            return "The parameter is too long (up to " + TEXT_MAX_LENGTH + " characters)";
        }
        if (input.levels() == null || input.levels().isEmpty()) {
            return "A task needs at least one level";
        }
        if (input.levels().size() > MAX_LEVELS) {
            return "A task can have up to " + MAX_LEVELS + " levels";
        }
        int previous = 0;
        for (RewardTrack.Level level : input.levels()) {
            if (level.requiredCount() <= previous) {
                return "Level counts must grow: each level needs more than the one before";
            }
            if (level.pointsReward() < 0 || level.pointsReward() > MAX_AMOUNT) {
                return "Level points must be between 0 and " + MAX_AMOUNT;
            }
            previous = level.requiredCount();
        }
        return null;
    }

    public static String validate(PrizeInput input) {
        String problem = validateId(input.trackId(), "track id");
        if (problem == null) {
            problem = validateId(input.id(), "prize id");
        }
        if (problem != null) {
            return problem;
        }
        if (input.requiredPoints() < 0 || input.rewardAmount() < 0 || input.productItemTypeId() < 0) {
            return "Points, amount and product type cannot be negative";
        }
        if (input.requiredPoints() > MAX_AMOUNT || input.rewardAmount() > MAX_AMOUNT) {
            return "Points and amount are at most " + MAX_AMOUNT;
        }
        if (input.productItemTypeId() > Short.MAX_VALUE) {
            return "The product type must fit in 16 bits";
        }
        String type =
                input.rewardType() == null ? "" : input.rewardType().trim().toLowerCase(Locale.ROOT);
        if (!REWARD_TYPES.contains(type)) {
            return "Unknown reward type: " + input.rewardType();
        }
        if (type.equals(QuestRewards.TYPE_BADGE)
                && (input.extraParams() == null || input.extraParams().isBlank())) {
            return "A badge prize needs the badge code in the extra parameters";
        }
        if (type.equals(QuestRewards.TYPE_FURNI)) {
            if (input.extraParams() == null || input.extraParams().isBlank()) {
                return "A furni prize needs the furni name (items_base.item_name) in the extra parameters";
            }
            if (input.rewardAmount() < 1 || input.rewardAmount() > QuestRewards.MAX_FURNI_PER_PRIZE) {
                return "A furni prize hands out 1 to " + QuestRewards.MAX_FURNI_PER_PRIZE + " copies";
            }
        }
        if (input.extraParams() != null && input.extraParams().length() > TEXT_MAX_LENGTH) {
            return "The extra parameters are too long (up to " + TEXT_MAX_LENGTH + " characters)";
        }
        return null;
    }

    public static String validateDelete(String entity, String trackId, String id) {
        String problem = validateId(trackId, "track id");
        if (problem != null) {
            return problem;
        }
        return switch (entity == null ? "" : entity) {
            case ENTITY_TRACK -> null;
            case ENTITY_TASK -> validateId(id, "task id");
            case ENTITY_PRIZE -> validateId(id, "prize id");
            default -> "Unknown entity: " + entity;
        };
    }

    /** The texts of a track: every key is plain and short, every value fits the column. */
    public static String validateTexts(String trackId, Map<String, String> texts) {
        String problem = validateId(trackId, "track id");
        if (problem != null) {
            return problem;
        }
        if (texts == null || texts.size() > MAX_TEXTS_PER_TRACK) {
            return "A track can have up to " + MAX_TEXTS_PER_TRACK + " texts";
        }
        for (Map.Entry<String, String> entry : texts.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > TEXT_KEY_MAX_LENGTH) {
                return "A text key is required (up to " + TEXT_KEY_MAX_LENGTH + " characters)";
            }
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (!(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-')) {
                    return "The text key " + key + " may only use letters, digits, '_', '-' and '.'";
                }
            }
            if (entry.getValue() == null || entry.getValue().length() > TEXT_VALUE_MAX_LENGTH) {
                return "The text " + key + " is too long (up to " + TEXT_VALUE_MAX_LENGTH + " characters)";
            }
        }
        return null;
    }

    private static String validateId(String id, String label) {
        if (id == null || id.isBlank()) {
            return "The " + label + " is required";
        }
        if (id.length() > ID_MAX_LENGTH) {
            return "The " + label + " is too long (up to " + ID_MAX_LENGTH + " characters)";
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.')) {
                return "The " + label + " may only use letters, digits, '_', '-' and '.'";
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ writes

    public static void saveTrack(TrackInput input) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO reward_tracks"
                        + " (id, theme, sort_order, starts_at, ends_at, has_premium, premium_task_points_boost,"
                        + " premium_instant_points, premium_cost_diamonds, premium_cost_credits, enabled)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE theme = VALUES(theme), sort_order = VALUES(sort_order),"
                        + " starts_at = VALUES(starts_at), ends_at = VALUES(ends_at), has_premium = VALUES(has_premium),"
                        + " premium_task_points_boost = VALUES(premium_task_points_boost),"
                        + " premium_instant_points = VALUES(premium_instant_points),"
                        + " premium_cost_diamonds = VALUES(premium_cost_diamonds),"
                        + " premium_cost_credits = VALUES(premium_cost_credits), enabled = VALUES(enabled)")) {
            statement.setString(1, input.id());
            statement.setString(2, input.theme());
            statement.setInt(3, input.sortOrder());
            statement.setInt(4, input.startsAt());
            statement.setInt(5, input.endsAt());
            statement.setBoolean(6, input.hasPremium());
            statement.setDouble(7, input.premiumBoostPercent() / 100.0);
            statement.setInt(8, input.premiumInstantPoints());
            statement.setInt(9, input.premiumCostDiamonds());
            statement.setInt(10, input.premiumCostCredits());
            statement.setBoolean(11, input.enabled());
            statement.execute();
        }
    }

    /** Writes the task and replaces its levels in one transaction. */
    public static void saveTask(TaskInput input) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO reward_track_tasks"
                        + " (track_id, id, action_type, parameter, premium, sort_order) VALUES (?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE action_type = VALUES(action_type), parameter = VALUES(parameter),"
                        + " premium = VALUES(premium), sort_order = VALUES(sort_order)")) {
                    statement.setString(1, input.trackId());
                    statement.setString(2, input.id());
                    statement.setString(3, input.actionType());
                    statement.setString(4, input.parameter() == null ? "" : input.parameter());
                    statement.setBoolean(5, input.premium());
                    statement.setInt(6, input.sortOrder());
                    statement.execute();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM reward_track_task_levels WHERE track_id = ? AND task_id = ?")) {
                    statement.setString(1, input.trackId());
                    statement.setString(2, input.id());
                    statement.execute();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO reward_track_task_levels"
                                + " (track_id, task_id, level, required_count, points_reward, premium) VALUES (?, ?, ?, ?, ?, ?)")) {
                    int index = 1;
                    for (RewardTrack.Level level : input.levels()) {
                        statement.setString(1, input.trackId());
                        statement.setString(2, input.id());
                        statement.setInt(3, index++);
                        statement.setInt(4, level.requiredCount());
                        statement.setInt(5, level.pointsReward());
                        statement.setBoolean(6, level.premium());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static void savePrize(PrizeInput input) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("INSERT INTO reward_track_prizes"
                        + " (track_id, id, required_points, product_item_type_id, reward_type, extra_params,"
                        + " reward_amount, premium, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
                        + " ON DUPLICATE KEY UPDATE required_points = VALUES(required_points),"
                        + " product_item_type_id = VALUES(product_item_type_id), reward_type = VALUES(reward_type),"
                        + " extra_params = VALUES(extra_params), reward_amount = VALUES(reward_amount),"
                        + " premium = VALUES(premium), sort_order = VALUES(sort_order)")) {
            statement.setString(1, input.trackId());
            statement.setString(2, input.id());
            statement.setInt(3, input.requiredPoints());
            statement.setInt(4, input.productItemTypeId());
            statement.setString(5, input.rewardType().trim().toLowerCase(Locale.ROOT));
            statement.setString(6, input.extraParams() == null ? "" : input.extraParams());
            statement.setInt(7, input.rewardAmount());
            statement.setBoolean(8, input.premium());
            statement.setInt(9, input.sortOrder());
            statement.execute();
        }
    }

    /**
     * Deletes a track with everything under it, a task with its levels, or a prize; the users' rows
     * that pointed at the deleted entity go with it, so a re-created id starts clean.
     */
    public static void delete(String entity, String trackId, String id) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                switch (entity) {
                    case ENTITY_TRACK -> {
                        run(connection, "DELETE FROM users_reward_track_prizes WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM users_reward_track_tasks WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM users_reward_tracks WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM reward_track_texts WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM reward_track_prizes WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM reward_track_task_levels WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM reward_track_tasks WHERE track_id = ?", trackId);
                        run(connection, "DELETE FROM reward_tracks WHERE id = ?", trackId);
                    }
                    case ENTITY_TASK -> {
                        run(
                                connection,
                                "DELETE FROM users_reward_track_tasks WHERE track_id = ? AND task_id = ?",
                                trackId,
                                id);
                        run(
                                connection,
                                "DELETE FROM reward_track_task_levels WHERE track_id = ? AND task_id = ?",
                                trackId,
                                id);
                        run(connection, "DELETE FROM reward_track_tasks WHERE track_id = ? AND id = ?", trackId, id);
                    }
                    case ENTITY_PRIZE -> {
                        run(
                                connection,
                                "DELETE FROM users_reward_track_prizes WHERE track_id = ? AND prize_id = ?",
                                trackId,
                                id);
                        run(connection, "DELETE FROM reward_track_prizes WHERE track_id = ? AND id = ?", trackId, id);
                    }
                    default -> throw new SQLException("Unknown entity " + entity);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Replaces every text of the track in one transaction. */
    public static void saveTexts(String trackId, Map<String, String> texts) throws SQLException {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            connection.setAutoCommit(false);
            try {
                run(connection, "DELETE FROM reward_track_texts WHERE track_id = ?", trackId);
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO reward_track_texts (track_id, text_key, value) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, String> entry : texts.entrySet()) {
                        if (entry.getValue().isBlank()) {
                            continue;
                        }
                        statement.setString(1, trackId);
                        statement.setString(2, entry.getKey().trim());
                        statement.setString(3, entry.getValue());
                        statement.addBatch();
                    }
                    statement.executeBatch();
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /** Every stored text, by track then key. */
    public static Map<String, Map<String, String>> loadTexts() {
        Map<String, Map<String, String>> texts = new LinkedHashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT track_id, text_key, value FROM reward_track_texts ORDER BY track_id, text_key");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                texts.computeIfAbsent(set.getString("track_id"), id -> new LinkedHashMap<>())
                        .put(set.getString("text_key"), set.getString("value"));
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not load the reward track texts", exception);
        }
        return texts;
    }

    /** How many users claimed each prize, keyed "&lt;track&gt;/&lt;prize&gt;". */
    public static Map<String, Integer> claimCounts() {
        Map<String, Integer> counts = new HashMap<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT track_id, prize_id, COUNT(*) AS claimed FROM users_reward_track_prizes GROUP BY track_id, prize_id");
                ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                counts.put(set.getString("track_id") + "/" + set.getString("prize_id"), set.getInt("claimed"));
            }
        } catch (SQLException exception) {
            LOGGER.error("Could not count the reward track claims", exception);
        }
        return counts;
    }

    /** The furni whose name contains the query, case-insensitive, up to the search limit. */
    public static List<FurniMatch> searchFurni(String query) {
        List<FurniMatch> matches = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return matches;
        }
        for (Item item :
                Emulator.getGameEnvironment().getItemManager().getItems().values()) {
            if (item == null || item.getName() == null) {
                continue;
            }
            if (item.getName().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(new FurniMatch(
                        item.getName(), item.getSpriteId(), item.getType().code.toLowerCase(Locale.ROOT)));
                if (matches.size() >= FURNI_SEARCH_LIMIT) {
                    break;
                }
            }
        }
        matches.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return matches;
    }

    private static void run(Connection connection, String sql, String... params) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setString(i + 1, params[i]);
            }
            statement.execute();
        }
    }
}
