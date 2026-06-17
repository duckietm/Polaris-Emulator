package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class WiredVariableReferenceSupport {
    public static final int TARGET_USER = 0;
    public static final int TARGET_ROOM = 3;
    public static final int SHARED_AVAILABILITY = 11;

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredVariableReferenceSupport.class);

    private static final ConcurrentHashMap<String, CachedUserAssignment> USER_ASSIGNMENT_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CachedRoomAssignment> ROOM_ASSIGNMENT_CACHE = new ConcurrentHashMap<>();

    private WiredVariableReferenceSupport() {
    }

    public static boolean isSharedAvailability(int availability) {
        return availability == SHARED_AVAILABILITY;
    }

    public static SharedDefinitionOption findSharedDefinition(Room room, int sourceRoomId, int sourceVariableItemId, int sourceTargetType) {
        if (room == null || sourceRoomId <= 0 || sourceVariableItemId <= 0) {
            return null;
        }

        for (RoomOption roomOption : loadRoomOptions(room)) {
            if (roomOption.getRoomId() != sourceRoomId) {
                continue;
            }

            for (SharedDefinitionOption definition : roomOption.getVariables()) {
                if (definition.getItemId() == sourceVariableItemId && definition.getTargetType() == sourceTargetType) {
                    return definition;
                }
            }
        }

        return null;
    }

    public static List<RoomOption> loadRoomOptions(Room room) {
        if (room == null || room.getOwnerId() <= 0) {
            return Collections.emptyList();
        }

        Map<Integer, RoomOption> optionsByRoomId = new LinkedHashMap<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT rooms.id AS room_id, rooms.name AS room_name, items.id AS item_id, items.wired_data, items_base.interaction_type " +
                     "FROM rooms " +
                     "INNER JOIN items ON rooms.id = items.room_id " +
                     "INNER JOIN items_base ON items.item_id = items_base.id " +
                     "WHERE rooms.owner_id = ? AND rooms.id <> ? AND items_base.interaction_type IN ('wf_var_user', 'wf_var_room') " +
                     "ORDER BY rooms.name ASC, items.id ASC")) {
            statement.setInt(1, room.getOwnerId());
            statement.setInt(2, room.getId());

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    SharedDefinitionOption definition = parseSharedDefinition(
                        set.getString("interaction_type"),
                        set.getInt("item_id"),
                        set.getString("wired_data"),
                        set.getInt("room_id"),
                        set.getString("room_name")
                    );

                    if (definition == null) {
                        continue;
                    }

                    RoomOption roomOption = optionsByRoomId.computeIfAbsent(
                        definition.getRoomId(),
                        key -> new RoomOption(definition.getRoomId(), definition.getRoomName(), new ArrayList<>())
                    );

                    roomOption.getVariables().add(definition);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load shared variable reference options for room {}", room.getId(), e);
        }

        List<RoomOption> result = new ArrayList<>(optionsByRoomId.values());

        for (RoomOption option : result) {
            option.getVariables().sort(Comparator.comparing(SharedDefinitionOption::getName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(SharedDefinitionOption::getItemId));
        }

        result.sort(Comparator.comparing(RoomOption::getRoomName, String.CASE_INSENSITIVE_ORDER).thenComparingInt(RoomOption::getRoomId));
        return result;
    }

    public static SharedUserAssignment getSharedUserAssignment(WiredExtraVariableReference reference, int userId) {
        if (reference == null || !reference.isUserReference() || userId <= 0) {
            return null;
        }

        String cacheKey = createUserCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId);
        CachedUserAssignment cachedValue = USER_ASSIGNMENT_CACHE.get(cacheKey);

        if (cachedValue != null) {
            return cachedValue.present ? cachedValue.toAssignment() : null;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT value, created_at, updated_at FROM room_user_wired_variables WHERE room_id = ? AND user_id = ? AND variable_item_id = ? LIMIT 1")) {
            statement.setInt(1, reference.getSourceRoomId());
            statement.setInt(2, userId);
            statement.setInt(3, reference.getSourceVariableItemId());

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    USER_ASSIGNMENT_CACHE.put(cacheKey, CachedUserAssignment.missing());
                    return null;
                }

                Integer value = null;
                int rawValue = set.getInt("value");
                if (!set.wasNull()) {
                    value = rawValue;
                }

                int createdAt = normalizeTimestamp(set.getInt("created_at"), 0);
                SharedUserAssignment assignment = new SharedUserAssignment(
                    value,
                    createdAt,
                    normalizeTimestamp(set.getInt("updated_at"), createdAt)
                );

                USER_ASSIGNMENT_CACHE.put(cacheKey, CachedUserAssignment.present(assignment));
                return assignment;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load shared wired user variable {} for room {} user {}", reference.getSourceVariableItemId(), reference.getSourceRoomId(), userId, e);
            return null;
        }
    }

    public static boolean assignSharedUserVariable(WiredExtraVariableReference reference, int userId, Integer value, boolean overrideExisting) {
        if (reference == null || !reference.isUserReference() || reference.isReadOnly() || userId <= 0 || !isSharedSourceStillAvailable(reference)) {
            return false;
        }

        Integer normalizedValue = reference.hasValue() ? value : null;
        SharedUserAssignment existingAssignment = getSharedUserAssignment(reference, userId);

        if (existingAssignment != null && !overrideExisting) {
            return false;
        }

        int now = Emulator.getIntUnixTimestamp();
        boolean overwritten = existingAssignment != null && overrideExisting;
        SharedUserAssignment nextAssignment = (existingAssignment == null || overwritten)
            ? new SharedUserAssignment(normalizedValue, now, now)
            : new SharedUserAssignment(normalizedValue, existingAssignment.getCreatedAt(), Objects.equals(existingAssignment.getValue(), normalizedValue) ? existingAssignment.getUpdatedAt() : now);

        if (!overwritten && existingAssignment != null && Objects.equals(existingAssignment.getValue(), normalizedValue)) {
            return false;
        }

        upsertSharedUserAssignment(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId, nextAssignment);
        USER_ASSIGNMENT_CACHE.put(createUserCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId), CachedUserAssignment.present(nextAssignment));
        return true;
    }

    public static boolean updateSharedUserVariable(WiredExtraVariableReference reference, int userId, Integer value) {
        if (reference == null || !reference.isUserReference() || reference.isReadOnly() || userId <= 0 || !reference.hasValue() || !isSharedSourceStillAvailable(reference)) {
            return false;
        }

        SharedUserAssignment existingAssignment = getSharedUserAssignment(reference, userId);
        if (existingAssignment == null || Objects.equals(existingAssignment.getValue(), value)) {
            return false;
        }

        SharedUserAssignment nextAssignment = new SharedUserAssignment(value, existingAssignment.getCreatedAt(), Emulator.getIntUnixTimestamp());
        upsertSharedUserAssignment(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId, nextAssignment);
        USER_ASSIGNMENT_CACHE.put(createUserCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId), CachedUserAssignment.present(nextAssignment));
        return true;
    }

    public static boolean removeSharedUserVariable(WiredExtraVariableReference reference, int userId) {
        if (reference == null || !reference.isUserReference() || reference.isReadOnly() || userId <= 0 || !isSharedSourceStillAvailable(reference)) {
            return false;
        }

        SharedUserAssignment existingAssignment = getSharedUserAssignment(reference, userId);
        if (existingAssignment == null) {
            return false;
        }

        deleteSharedUserAssignment(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId);
        USER_ASSIGNMENT_CACHE.put(createUserCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId(), userId), CachedUserAssignment.missing());
        return true;
    }

    public static void cacheSharedUserAssignment(int sourceRoomId, int sourceVariableItemId, int userId, Integer value, int createdAt, int updatedAt) {
        USER_ASSIGNMENT_CACHE.put(createUserCacheKey(sourceRoomId, sourceVariableItemId, userId), CachedUserAssignment.present(new SharedUserAssignment(value, createdAt, updatedAt)));
    }

    public static void clearSharedUserAssignment(int sourceRoomId, int sourceVariableItemId, int userId) {
        USER_ASSIGNMENT_CACHE.put(createUserCacheKey(sourceRoomId, sourceVariableItemId, userId), CachedUserAssignment.missing());
    }

    public static void clearSharedUserDefinition(int sourceRoomId, int sourceVariableItemId) {
        String prefix = createDefinitionPrefix(sourceRoomId, sourceVariableItemId) + ":";
        USER_ASSIGNMENT_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    /**
     * Drops all cached shared-variable assignments belonging to a room. Both
     * caches are keyed "roomId:itemId[:userId]", so the trailing colon makes the
     * prefix match the exact room id. Called on room dispose so the static caches
     * don't retain entries for the JVM lifetime.
     */
    public static void invalidateRoom(int roomId) {
        String prefix = roomId + ":";
        USER_ASSIGNMENT_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
        ROOM_ASSIGNMENT_CACHE.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    public static SharedRoomAssignment getSharedRoomAssignment(WiredExtraVariableReference reference) {
        if (reference == null || !reference.isRoomReference()) {
            return null;
        }

        String cacheKey = createRoomCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId());
        CachedRoomAssignment cachedValue = ROOM_ASSIGNMENT_CACHE.get(cacheKey);

        if (cachedValue != null) {
            return cachedValue.present ? cachedValue.toAssignment() : null;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT value, updated_at FROM room_wired_variables WHERE room_id = ? AND variable_item_id = ? LIMIT 1")) {
            statement.setInt(1, reference.getSourceRoomId());
            statement.setInt(2, reference.getSourceVariableItemId());

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    ROOM_ASSIGNMENT_CACHE.put(cacheKey, CachedRoomAssignment.missing());
                    return null;
                }

                SharedRoomAssignment assignment = new SharedRoomAssignment(set.getInt("value"), normalizeTimestamp(set.getInt("updated_at"), 0));
                ROOM_ASSIGNMENT_CACHE.put(cacheKey, CachedRoomAssignment.present(assignment));
                return assignment;
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to load shared wired room variable {} for room {}", reference.getSourceVariableItemId(), reference.getSourceRoomId(), e);
            return null;
        }
    }

    public static boolean updateSharedRoomVariable(WiredExtraVariableReference reference, int value) {
        if (reference == null || !reference.isRoomReference() || reference.isReadOnly() || !isSharedSourceStillAvailable(reference)) {
            return false;
        }

        SharedRoomAssignment existingAssignment = getSharedRoomAssignment(reference);
        if (existingAssignment != null && existingAssignment.getValue() == value) {
            return false;
        }

        SharedRoomAssignment nextAssignment = new SharedRoomAssignment(value, Emulator.getIntUnixTimestamp());
        upsertSharedRoomAssignment(reference.getSourceRoomId(), reference.getSourceVariableItemId(), nextAssignment);
        ROOM_ASSIGNMENT_CACHE.put(createRoomCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId()), CachedRoomAssignment.present(nextAssignment));
        return true;
    }

    public static boolean removeSharedRoomVariable(WiredExtraVariableReference reference) {
        if (reference == null || !reference.isRoomReference() || reference.isReadOnly() || !isSharedSourceStillAvailable(reference)) {
            return false;
        }

        SharedRoomAssignment existingAssignment = getSharedRoomAssignment(reference);
        if (existingAssignment == null) {
            return false;
        }

        deleteSharedRoomAssignment(reference.getSourceRoomId(), reference.getSourceVariableItemId());
        ROOM_ASSIGNMENT_CACHE.put(createRoomCacheKey(reference.getSourceRoomId(), reference.getSourceVariableItemId()), CachedRoomAssignment.missing());
        return true;
    }

    public static void cacheSharedRoomAssignment(int sourceRoomId, int sourceVariableItemId, int value, int updatedAt) {
        ROOM_ASSIGNMENT_CACHE.put(createRoomCacheKey(sourceRoomId, sourceVariableItemId), CachedRoomAssignment.present(new SharedRoomAssignment(value, updatedAt)));
    }

    public static void clearSharedRoomDefinition(int sourceRoomId, int sourceVariableItemId) {
        ROOM_ASSIGNMENT_CACHE.put(createRoomCacheKey(sourceRoomId, sourceVariableItemId), CachedRoomAssignment.missing());
    }

    private static SharedDefinitionOption parseSharedDefinition(String interactionType, int itemId, String wiredData, int roomId, String roomName) {
        if ("wf_var_user".equals(interactionType)) {
            UserDefinitionData data = parseUserDefinitionData(wiredData);
            if (data == null || !isSharedAvailability(data.availability) || data.variableName.isEmpty()) {
                return null;
            }

            return new SharedDefinitionOption(roomId, roomName, itemId, data.variableName, TARGET_USER, data.hasValue);
        }

        if ("wf_var_room".equals(interactionType)) {
            RoomDefinitionData data = parseRoomDefinitionData(wiredData);
            if (data == null || !isSharedAvailability(data.availability) || data.variableName.isEmpty()) {
                return null;
            }

            return new SharedDefinitionOption(roomId, roomName, itemId, data.variableName, TARGET_ROOM, true);
        }

        return null;
    }

    private static UserDefinitionData parseUserDefinitionData(String wiredData) {
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return null;
        }

        UserDefinitionData data = WiredManager.getGson().fromJson(wiredData, UserDefinitionData.class);
        if (data == null) {
            return null;
        }

        data.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        return data;
    }

    private static RoomDefinitionData parseRoomDefinitionData(String wiredData) {
        if (wiredData == null || wiredData.isEmpty() || !wiredData.startsWith("{")) {
            return null;
        }

        RoomDefinitionData data = WiredManager.getGson().fromJson(wiredData, RoomDefinitionData.class);
        if (data == null) {
            return null;
        }

        data.variableName = WiredVariableNameValidator.normalizeLegacy(data.variableName);
        return data;
    }

    private static boolean isSharedSourceStillAvailable(WiredExtraVariableReference reference) {
        if (reference == null || reference.getSourceRoomId() <= 0 || reference.getSourceVariableItemId() <= 0) {
            return false;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT items.wired_data, items_base.interaction_type " +
                     "FROM items INNER JOIN items_base ON items.item_id = items_base.id " +
                     "WHERE items.id = ? AND items.room_id = ? LIMIT 1")) {
            statement.setInt(1, reference.getSourceVariableItemId());
            statement.setInt(2, reference.getSourceRoomId());

            try (ResultSet set = statement.executeQuery()) {
                if (!set.next()) {
                    return false;
                }

                SharedDefinitionOption definition = parseSharedDefinition(
                    set.getString("interaction_type"),
                    reference.getSourceVariableItemId(),
                    set.getString("wired_data"),
                    reference.getSourceRoomId(),
                    ""
                );

                return definition != null && definition.getTargetType() == reference.getSourceTargetType();
            }
        } catch (SQLException e) {
            LOGGER.error("Failed to validate shared wired variable source {} in room {}", reference.getSourceVariableItemId(), reference.getSourceRoomId(), e);
            return false;
        }
    }

    private static void upsertSharedUserAssignment(int sourceRoomId, int sourceVariableItemId, int userId, SharedUserAssignment assignment) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO room_user_wired_variables (room_id, user_id, variable_item_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)")) {
                statement.setInt(1, sourceRoomId);
                statement.setInt(2, userId);
                statement.setInt(3, sourceVariableItemId);

                if (assignment.getValue() == null) {
                    statement.setNull(4, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(4, assignment.getValue());
                }

                statement.setInt(5, assignment.getCreatedAt());
                statement.setInt(6, assignment.getUpdatedAt());
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to store shared wired user variable {} for room {} user {}", sourceVariableItemId, sourceRoomId, userId, e);
            }
        });
    }

    private static void deleteSharedUserAssignment(int sourceRoomId, int sourceVariableItemId, int userId) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM room_user_wired_variables WHERE room_id = ? AND user_id = ? AND variable_item_id = ?")) {
                statement.setInt(1, sourceRoomId);
                statement.setInt(2, userId);
                statement.setInt(3, sourceVariableItemId);
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete shared wired user variable {} for room {} user {}", sourceVariableItemId, sourceRoomId, userId, e);
            }
        });
    }

    private static void upsertSharedRoomAssignment(int sourceRoomId, int sourceVariableItemId, SharedRoomAssignment assignment) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO room_wired_variables (room_id, variable_item_id, value, created_at, updated_at) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE value = VALUES(value), updated_at = VALUES(updated_at)")) {
                statement.setInt(1, sourceRoomId);
                statement.setInt(2, sourceVariableItemId);
                statement.setInt(3, assignment.getValue());
                statement.setInt(4, 0);
                statement.setInt(5, assignment.getUpdatedAt());
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to store shared wired room variable {} for room {}", sourceVariableItemId, sourceRoomId, e);
            }
        });
    }

    private static void deleteSharedRoomAssignment(int sourceRoomId, int sourceVariableItemId) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM room_wired_variables WHERE room_id = ? AND variable_item_id = ?")) {
                statement.setInt(1, sourceRoomId);
                statement.setInt(2, sourceVariableItemId);
                statement.executeUpdate();
            } catch (SQLException e) {
                LOGGER.error("Failed to delete shared wired room variable {} for room {}", sourceVariableItemId, sourceRoomId, e);
            }
        });
    }

    private static String createDefinitionPrefix(int sourceRoomId, int sourceVariableItemId) {
        return sourceRoomId + ":" + sourceVariableItemId;
    }

    private static String createUserCacheKey(int sourceRoomId, int sourceVariableItemId, int userId) {
        return createDefinitionPrefix(sourceRoomId, sourceVariableItemId) + ":" + userId;
    }

    private static String createRoomCacheKey(int sourceRoomId, int sourceVariableItemId) {
        return createDefinitionPrefix(sourceRoomId, sourceVariableItemId);
    }

    private static int normalizeTimestamp(int value, int fallback) {
        if (value > 0) {
            return value;
        }

        if (fallback > 0) {
            return fallback;
        }

        return Emulator.getIntUnixTimestamp();
    }

    public static class RoomOption {
        private final int roomId;
        private final String roomName;
        private final List<SharedDefinitionOption> variables;

        public RoomOption(int roomId, String roomName, List<SharedDefinitionOption> variables) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.variables = variables;
        }

        public int getRoomId() {
            return this.roomId;
        }

        public String getRoomName() {
            return this.roomName;
        }

        public List<SharedDefinitionOption> getVariables() {
            return this.variables;
        }
    }

    public static class SharedDefinitionOption {
        private final int roomId;
        private final String roomName;
        private final int itemId;
        private final String name;
        private final int targetType;
        private final boolean hasValue;

        public SharedDefinitionOption(int roomId, String roomName, int itemId, String name, int targetType, boolean hasValue) {
            this.roomId = roomId;
            this.roomName = roomName;
            this.itemId = itemId;
            this.name = name;
            this.targetType = targetType;
            this.hasValue = hasValue;
        }

        public int getRoomId() {
            return this.roomId;
        }

        public String getRoomName() {
            return this.roomName;
        }

        public int getItemId() {
            return this.itemId;
        }

        public String getName() {
            return this.name;
        }

        public int getTargetType() {
            return this.targetType;
        }

        public boolean hasValue() {
            return this.hasValue;
        }
    }

    public static class SharedUserAssignment {
        private final Integer value;
        private final int createdAt;
        private final int updatedAt;

        public SharedUserAssignment(Integer value, int createdAt, int updatedAt) {
            this.value = value;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }

        public Integer getValue() {
            return this.value;
        }

        public int getCreatedAt() {
            return this.createdAt;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
        }
    }

    public static class SharedRoomAssignment {
        private final int value;
        private final int updatedAt;

        public SharedRoomAssignment(int value, int updatedAt) {
            this.value = value;
            this.updatedAt = updatedAt;
        }

        public int getValue() {
            return this.value;
        }

        public int getUpdatedAt() {
            return this.updatedAt;
        }
    }

    private static class CachedUserAssignment {
        private final boolean present;
        private final SharedUserAssignment assignment;

        private CachedUserAssignment(boolean present, SharedUserAssignment assignment) {
            this.present = present;
            this.assignment = assignment;
        }

        private static CachedUserAssignment present(SharedUserAssignment assignment) {
            return new CachedUserAssignment(true, assignment);
        }

        private static CachedUserAssignment missing() {
            return new CachedUserAssignment(false, null);
        }

        private SharedUserAssignment toAssignment() {
            return this.assignment;
        }
    }

    private static class CachedRoomAssignment {
        private final boolean present;
        private final SharedRoomAssignment assignment;

        private CachedRoomAssignment(boolean present, SharedRoomAssignment assignment) {
            this.present = present;
            this.assignment = assignment;
        }

        private static CachedRoomAssignment present(SharedRoomAssignment assignment) {
            return new CachedRoomAssignment(true, assignment);
        }

        private static CachedRoomAssignment missing() {
            return new CachedRoomAssignment(false, null);
        }

        private SharedRoomAssignment toAssignment() {
            return this.assignment;
        }
    }

    private static class UserDefinitionData {
        String variableName;
        boolean hasValue;
        int availability;
    }

    private static class RoomDefinitionData {
        String variableName;
        int availability;
    }
}
