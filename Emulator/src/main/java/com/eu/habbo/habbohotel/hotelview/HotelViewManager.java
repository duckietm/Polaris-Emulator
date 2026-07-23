package com.eu.habbo.habbohotel.hotelview;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewLandingComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.Instant;

public class HotelViewManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelViewManager.class);

    private final HallOfFame hallOfFame;
    private final NewsList newsList;
    private volatile HotelViewScene scene = HotelViewScene.EMPTY;
    private volatile List<HotelViewSlot> slots = Collections.emptyList();

    public HotelViewManager() {
        long millis = System.currentTimeMillis();
        this.hallOfFame = new HallOfFame();
        this.newsList = new NewsList();
        this.reloadLandingView();

        LOGGER.info("Hotelview Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public HallOfFame getHallOfFame() {
        return this.hallOfFame;
    }

    public NewsList getNewsList() {
        return this.newsList;
    }

    public HotelViewScene getScene() {
        return this.scene;
    }

    public List<HotelViewSlot> getSlots() {
        return this.slots;
    }

    public List<HotelViewSlot> getSlotsForUser(int userId) {
        if (userId <= 0) return this.slots;

        Map<Integer, Integer> userVotes = new HashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT slot_id, option_id FROM hotelview_landing_votes WHERE user_id = ?")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) userVotes.put(set.getInt("slot_id"), set.getInt("option_id"));
            }
        } catch (SQLException e) {
            LOGGER.warn("Could not load HotelView community votes for user {}", userId, e);
            return this.slots;
        }

        if (userVotes.isEmpty()) return this.slots;

        List<HotelViewSlot> result = new ArrayList<>(this.slots.size());

        for (HotelViewSlot slot : this.slots) {
            Integer optionId = userVotes.get(slot.id());
            result.add(optionId == null ? slot : new HotelViewSlot(
                    slot.id(), slot.enabled(), slot.type(), slot.title(), slot.body(), slot.imageUrl(), slot.buttonText(), slot.link(), slot.progress(), slot.progressLabel(), withUserVote(slot.configJson(), optionId)
            ));
        }

        return result;
    }

    public synchronized void reloadLandingView() {
        HotelViewScene loadedScene = HotelViewScene.EMPTY;
        List<HotelViewSlot> loadedSlots = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT background_url, left_url, right_url, drape_url, left_x, left_y, right_x, right_y, drape_x, drape_y, hall_of_fame_x, hall_of_fame_y, hall_of_fame_enabled, hall_of_fame_mode, hall_of_fame_currency_type FROM hotelview_landing_settings WHERE id = 1");
                 ResultSet set = statement.executeQuery()) {
                if (set.next()) {
                    boolean hallOfFameEnabled = set.getBoolean("hall_of_fame_enabled");
                    String hallOfFameMode = normalizeHallOfFameMode(value(set.getString("hall_of_fame_mode")));
                    int hallOfFameCurrencyType = Math.max(0, set.getInt("hall_of_fame_currency_type"));
                    loadedScene = new HotelViewScene(
                            value(set.getString("background_url")),
                            value(set.getString("left_url")),
                            value(set.getString("right_url")),
                            value(set.getString("drape_url")),
                            set.getInt("left_x"),
                            set.getInt("left_y"),
                            set.getInt("right_x"),
                            set.getInt("right_y"),
                            set.getInt("drape_x"),
                            set.getInt("drape_y"),
                            set.getInt("hall_of_fame_x"),
                            set.getInt("hall_of_fame_y"),
                            hallOfFameEnabled,
                            hallOfFameMode,
                            hallOfFameCurrencyType,
                            hallOfFameEnabled ? loadHallOfFameUsers(connection, hallOfFameMode, hallOfFameCurrencyType) : List.of()
                    );
                }
            }

            Map<Integer, Map<Integer, Integer>> voteCounts = loadVoteCounts(connection);

            try (PreparedStatement statement = connection.prepareStatement("SELECT id, enabled, type, title, body, image_url, button_text, link, progress, progress_label, config_json FROM hotelview_landing_slots ORDER BY id");
                 ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    int id = set.getInt("id");
                    if (id > 5) continue;

                    loadedSlots.add(new HotelViewSlot(
                            id,
                            set.getBoolean("enabled"),
                            value(set.getString("type")),
                            value(set.getString("title")),
                            value(set.getString("body")),
                            value(set.getString("image_url")),
                            value(set.getString("button_text")),
                            value(set.getString("link")),
                            set.getInt("progress"),
                            value(set.getString("progress_label")),
                            withRuntimeConfig(connection, value(set.getString("type")), withVoteCounts(value(set.getString("config_json")), voteCounts.get(id)))
                    ));
                }
            }
        } catch (SQLException e) {
            LOGGER.warn("Hotelview landing data could not be loaded. Apply database update 025_hotelview_landing.sql.", e);
        }

        this.scene = loadedScene;
        this.slots = Collections.unmodifiableList(loadedSlots);
    }

    public synchronized boolean saveSlot(HotelViewSlot slot) {
        if (slot.id() < 1 || slot.id() > 5) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE hotelview_landing_slots SET enabled = ?, type = ?, title = ?, body = ?, image_url = ?, button_text = ?, link = ?, progress = ?, progress_label = ?, config_json = ? WHERE id = ?")) {
            statement.setBoolean(1, slot.enabled());
            statement.setString(2, slot.type());
            statement.setString(3, slot.title());
            statement.setString(4, slot.body());
            statement.setString(5, slot.imageUrl());
            statement.setString(6, slot.buttonText());
            statement.setString(7, slot.link());
            statement.setInt(8, Math.max(0, Math.min(100, slot.progress())));
            statement.setString(9, slot.progressLabel());
            statement.setString(10, withoutRuntimeVoteConfig(slot.configJson()));
            statement.setInt(11, slot.id());

            if (statement.executeUpdate() != 1) return false;

            this.reloadLandingView();
            this.broadcastLandingView();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save hotelview landing slot {}", slot.id(), e);
            return false;
        }
    }

    public synchronized boolean saveScene(HotelViewScene scene) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE hotelview_landing_settings SET background_url = ?, left_url = ?, right_url = ?, drape_url = ?, left_x = ?, left_y = ?, right_x = ?, right_y = ?, drape_x = ?, drape_y = ?, hall_of_fame_x = ?, hall_of_fame_y = ?, hall_of_fame_enabled = ?, hall_of_fame_mode = ?, hall_of_fame_currency_type = ? WHERE id = 1")) {
            statement.setString(1, scene.backgroundUrl());
            statement.setString(2, scene.leftUrl());
            statement.setString(3, scene.rightUrl());
            statement.setString(4, scene.drapeUrl());
            statement.setInt(5, scene.leftX());
            statement.setInt(6, scene.leftY());
            statement.setInt(7, scene.rightX());
            statement.setInt(8, scene.rightY());
            statement.setInt(9, scene.drapeX());
            statement.setInt(10, scene.drapeY());
            statement.setInt(11, scene.hallOfFameX());
            statement.setInt(12, scene.hallOfFameY());
            statement.setBoolean(13, scene.hallOfFameEnabled());
            statement.setString(14, normalizeHallOfFameMode(scene.hallOfFameMode()));
            statement.setInt(15, Math.max(0, scene.hallOfFameCurrencyType()));

            if (statement.executeUpdate() != 1) return false;

            this.reloadLandingView();
            this.broadcastLandingView();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save hotelview landing scene", e);
            return false;
        }
    }

    public synchronized void reloadAndBroadcastLandingView() {
        this.reloadLandingView();
        this.broadcastLandingView();
    }

    public synchronized boolean voteCommunityGoal(int slotId, int optionId, int userId) {
        if (slotId < 1 || slotId > 5 || optionId < 1 || userId <= 0) return false;

        HotelViewSlot slot = this.slots.stream().filter(value -> value.id() == slotId && "communitygoal".equals(value.type())).findFirst().orElse(null);

        if (slot == null || !hasVoteOption(slot.configJson(), optionId)) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT IGNORE INTO hotelview_landing_votes (slot_id, user_id, option_id)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setInt(1, slotId);
            statement.setInt(2, userId);
            statement.setInt(3, optionId);
            boolean voteInserted = statement.executeUpdate() == 1;
            if (voteInserted) {
                Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(userId);
                if (habbo != null) awardVoteRewards(habbo, slot.configJson(), optionId);
            }
            if (voteInserted) {
                this.reloadLandingView();
                this.broadcastLandingView();
            }
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to save community goal vote for slot {}", slotId, e);
            return false;
        }
    }

    public synchronized boolean resetCommunityGoalVotes(int slotId) {
        if (slotId < 1 || slotId > 5) return false;

        HotelViewSlot slot = this.slots.stream().filter(value -> value.id() == slotId && "communitygoal".equals(value.type())).findFirst().orElse(null);

        if (slot == null) return false;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM hotelview_landing_votes WHERE slot_id = ?")) {
            statement.setInt(1, slotId);
            statement.executeUpdate();
            this.reloadLandingView();
            this.broadcastLandingView();
            return true;
        } catch (SQLException e) {
            LOGGER.error("Failed to reset community goal votes for slot {}", slotId, e);
            return false;
        }
    }

    private void broadcastLandingView() {
        for (Habbo habbo : Emulator.getGameEnvironment().getHabboManager().getOnlineHabbos().values()) {
            if (habbo == null || habbo.getClient() == null) continue;

            habbo.getClient().sendResponse(new HotelViewLandingComposer(
                    habbo.getHabboInfo().getRank().getId() >= 7,
                    this.scene,
                    this.getSlotsForUser(habbo.getHabboInfo().getId())
            ));
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeHallOfFameMode(String mode) {
        return switch (mode) {
            case "online_time", "achievement_score", "currency" -> mode;
            default -> "latest_registered";
        };
    }

    private static List<HotelViewHallOfFameUser> loadHallOfFameUsers(Connection connection, String mode, int currencyType) throws SQLException {
        String query = switch (mode) {
            case "online_time" -> "SELECT u.id, u.username, u.look, u.gender FROM users u INNER JOIN users_settings s ON s.user_id = u.id ORDER BY s.online_time DESC, u.id DESC LIMIT 10";
            case "achievement_score" -> "SELECT u.id, u.username, u.look, u.gender FROM users u INNER JOIN users_settings s ON s.user_id = u.id ORDER BY s.achievement_score DESC, u.id DESC LIMIT 10";
            case "currency" -> "SELECT u.id, u.username, u.look, u.gender FROM users u INNER JOIN users_currency c ON c.user_id = u.id WHERE c.type = ? ORDER BY c.amount DESC, u.id DESC LIMIT 10";
            default -> "SELECT id, username, look, gender FROM users ORDER BY account_created DESC, id DESC LIMIT 10";
        };

        List<HotelViewHallOfFameUser> users = new ArrayList<>(10);

        try (PreparedStatement statement = connection.prepareStatement(query)) {
            if ("currency".equals(mode)) statement.setInt(1, currencyType);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    users.add(new HotelViewHallOfFameUser(
                            set.getInt("id"),
                            value(set.getString("username")),
                            value(set.getString("look")),
                            value(set.getString("gender"))
                    ));
                }
            }
        }

        return users;
    }

    private static Map<Integer, Map<Integer, Integer>> loadVoteCounts(Connection connection) throws SQLException {
        Map<Integer, Map<Integer, Integer>> counts = new HashMap<>();

        try (PreparedStatement statement = connection.prepareStatement("SELECT slot_id, option_id, COUNT(*) AS votes FROM hotelview_landing_votes GROUP BY slot_id, option_id");
             ResultSet set = statement.executeQuery()) {
            while (set.next()) {
                counts.computeIfAbsent(set.getInt("slot_id"), ignored -> new HashMap<>()).put(set.getInt("option_id"), set.getInt("votes"));
            }
        }

        return counts;
    }

    private static boolean hasVoteOption(String configJson, int optionId) {
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return false;

            JsonElement options = parsed.getAsJsonObject().get("voteOptions");
            if (options == null || !options.isJsonArray()) return false;

            for (JsonElement option : options.getAsJsonArray()) {
                if (option.isJsonObject() && option.getAsJsonObject().has("id") && option.getAsJsonObject().get("id").getAsInt() == optionId) return true;
            }
        } catch (RuntimeException ignored) {
        }

        return false;
    }

    private static String getVoteBadgeCode(String configJson, int optionId) {
        JsonObject option = getVoteOption(configJson, optionId);
        if (option == null || !option.has("badgeCode")) return "";

        try {
            return option.get("badgeCode").getAsString().trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static JsonObject getVoteOption(String configJson, int optionId) {
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return null;

            JsonElement options = parsed.getAsJsonObject().get("voteOptions");
            if (options == null || !options.isJsonArray()) return null;

            for (JsonElement option : options.getAsJsonArray()) {
                if (!option.isJsonObject()) continue;

                JsonObject value = option.getAsJsonObject();
                if (value.has("id") && value.get("id").getAsInt() == optionId) return value;
            }
        } catch (RuntimeException ignored) {
        }

        return null;
    }

    private static void awardVoteRewards(Habbo habbo, String configJson, int optionId) {
        JsonObject option = getVoteOption(configJson, optionId);
        if (option == null) return;

        String badgeCode = getVoteBadgeCode(configJson, optionId);
        if (!badgeCode.isBlank()) habbo.addBadge(badgeCode, "Community Goal");

        int furniId = getPositiveInt(option, "furniId");
        if (furniId > 0) {
            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(furniId);
            if (baseItem != null) {
                HabboItem reward = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
                if (reward != null) {
                    habbo.getInventory().getItemsComponent().addItem(reward);
                    if (habbo.getClient() != null) {
                        habbo.getClient().sendResponse(new AddHabboItemComposer(reward));
                        habbo.getClient().sendResponse(new InventoryRefreshComposer());
                    }
                }
            }
        }

        int currencyAmount = getPositiveInt(option, "currencyAmount");
        if (currencyAmount > 0 && option.has("currencyType")) {
            try {
                int currencyType = option.get("currencyType").getAsInt();
                if (currencyType >= 0) habbo.givePoints(currencyType, currencyAmount);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private static int getPositiveInt(JsonObject value, String key) {
        try {
            return value.has(key) ? Math.max(0, value.get(key).getAsInt()) : 0;
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String withVoteCounts(String configJson, Map<Integer, Integer> counts) {
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return "{}";

            JsonObject config = parsed.getAsJsonObject();
            if (!config.has("voteOptions")) return config.toString();

            config.remove("userVote");
            JsonObject voteCounts = new JsonObject();
            if (counts != null) counts.forEach((optionId, total) -> voteCounts.addProperty(String.valueOf(optionId), total));
            config.add("voteCounts", voteCounts);
            return config.toString();
        } catch (RuntimeException ignored) {
            return "{}";
        }
    }

    private static String withUserVote(String configJson, int optionId) {
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return configJson;

            JsonObject config = parsed.getAsJsonObject();
            config.addProperty("userVote", optionId);
            return config.toString();
        } catch (RuntimeException ignored) {
            return configJson;
        }
    }

    private static String withoutRuntimeVoteConfig(String configJson) {
        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            if (!parsed.isJsonObject()) return configJson;

            JsonObject config = parsed.getAsJsonObject();
            config.remove("userVote");
            config.remove("voteCounts");
            return config.toString();
        } catch (RuntimeException ignored) {
            return configJson;
        }
    }

    private static String withRuntimeConfig(Connection connection, String type, String configJson) {
        if (!"nextlimitedrarecountdown".equals(type) && !"expiringcatalogpage".equals(type) && !"expiringcatalogpagesmall".equals(type)) return configJson;

        try {
            JsonElement parsed = JsonParser.parseString(configJson);
            JsonObject config = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();

            if ("nextlimitedrarecountdown".equals(type)) {
                boolean useServerLTD = !config.has("useServerLTD") || config.get("useServerLTD").getAsBoolean();
                boolean enabled = Emulator.getConfig().getBoolean("hotel.view.ltdcountdown.enabled");
                int timestamp = Emulator.getConfig().getInt("hotel.view.ltdcountdown.timestamp");
                int pageId = Emulator.getConfig().getInt("hotel.view.ltdcountdown.pageid");

                if (useServerLTD && enabled && timestamp > 0) config.addProperty("endsAt", Instant.ofEpochSecond(timestamp).toString());
                if (useServerLTD && enabled && pageId > 0) config.addProperty("catalogPage", String.valueOf(pageId));
            } else if (config.has("featuredSlot")) {
                applyFeaturedCatalogCountdown(connection, config);
            }

            return config.toString();
        } catch (RuntimeException | SQLException ignored) {
            return configJson;
        }
    }

    private static void applyFeaturedCatalogCountdown(Connection connection, JsonObject config) throws SQLException {
        int slotId = config.get("featuredSlot").getAsInt();
        if (slotId < 1) return;

        try (PreparedStatement statement = connection.prepareStatement("SELECT expire_timestamp, page_name, page_id FROM catalog_featured_pages WHERE slot_id = ?")) {
            statement.setInt(1, slotId);

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) return;

                int timestamp = set.getInt("expire_timestamp");
                String pageName = value(set.getString("page_name"));
                int pageId = set.getInt("page_id");

                if (timestamp > 0) config.addProperty("endsAt", Instant.ofEpochSecond(timestamp).toString());
                if (!pageName.isBlank()) config.addProperty("catalogPage", pageName);
                else if (pageId > 0) config.addProperty("catalogPage", String.valueOf(pageId));
            }
        }
    }

    public void dispose() {
        LOGGER.info("HotelView Manager -> Disposed!");
    }

}
