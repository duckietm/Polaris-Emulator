package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomConstructionCharacterizationTest {

    private Database originalDatabase;
    private ConfigurationManager originalConfig;
    private RecordingDataSource dataSource;

    @TempDir
    Path tempDirectory;

    @BeforeEach
    void installRuntimeInputs() throws Exception {
        Field databaseField = Emulator.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        this.originalDatabase = (Database) databaseField.get(null);
        this.dataSource = new RecordingDataSource();
        databaseField.set(null, databaseUsing(this.dataSource));

        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        this.originalConfig = (ConfigurationManager) configField.get(null);
        Path config = this.tempDirectory.resolve("config.ini");
        Files.writeString(config, "hotel.flood.mute.time=30\n");
        configField.set(null, new ConfigurationManager(config.toString()));
    }

    @AfterEach
    void restoreRuntimeInputs() throws Exception {
        Field databaseField = Emulator.class.getDeclaredField("database");
        databaseField.setAccessible(true);
        databaseField.set(null, this.originalDatabase);

        Field configField = Emulator.class.getDeclaredField("config");
        configField.setAccessible(true);
        configField.set(null, this.originalConfig);
    }

    @Test
    void publicResultSetConstructorPreservesParsedStateAndEagerBanLoading() throws Exception {
        Room room = new Room(roomRow());

        assertEquals(41, room.getId());
        assertEquals(7, room.getOwnerId());
        assertEquals("owner", room.getOwnerName());
        assertEquals("Characterized room", room.getName());
        assertEquals("Existing constructor behavior", room.getDescription());
        assertEquals("secret", room.getPassword());
        assertEquals(RoomState.PASSWORD, room.getState());
        assertEquals(25, room.getUsersMax());
        assertEquals(88, room.getScore());
        assertEquals(3, room.getCategory());
        assertEquals("1.1", room.getFloorPaint());
        assertEquals("2.2", room.getWallPaint());
        assertEquals("3.3", room.getBackgroundPaint());
        assertEquals(2, room.getWallSize());
        assertEquals(-1, room.getWallHeight());
        assertEquals(4, room.getFloorSize());
        assertEquals("retro,polaris", room.getTags());
        assertEquals(2, room.getTradeMode());
        assertTrue(room.moveDiagonally());
        assertTrue(room.isPublicRoom());
        assertTrue(room.isStaffPromotedRoom());
        assertTrue(room.isAllowPets());
        assertFalse(room.isAllowPetsEat());
        assertTrue(room.isAllowWalkthrough());
        assertTrue(room.isAllowUnderpass());
        assertTrue(room.isHideWall());
        assertTrue(room.isYoutubeEnabled());
        assertFalse(room.isSoundboardEnabled());
        assertTrue(room.isBuildersClubTrialLocked());
        assertEquals(RoomState.LOCKED, room.getBuildersClubOriginalState());
        assertTrue(room.isPreLoaded());

        assertEquals(
                "SELECT users.username, users.id, room_bans.* FROM room_bans "
                        + "INNER JOIN users ON room_bans.user_id = users.id "
                        + "WHERE ends > ? AND room_bans.room_id = ?",
                this.dataSource.sql);
        assertEquals(41, this.dataSource.parameters.get(2));
        assertTrue(((Integer) this.dataSource.parameters.get(1)) > 0);
    }

    @Test
    void publicConstructorBuildsStableManagersAndLiveCollections() throws Exception {
        Room room = new Room(roomRow());

        assertNotNull(room.getTileManager());
        assertNotNull(room.getGameManager());
        assertNotNull(room.getTradeManager());
        assertNotNull(room.getPromotionManager());
        assertNotNull(room.getWordQuizManager());
        assertNotNull(room.getRightsManager());
        assertNotNull(room.getUnitManager());
        assertNotNull(room.getItemManager());
        assertNotNull(room.getChatManager());
        assertNotNull(room.getRollerManager());
        assertNotNull(room.getMessagingManager());
        assertNotNull(room.getCycleManager());
        assertNotNull(room.getUserVariableManager());
        assertNotNull(room.getFurniVariableManager());
        assertNotNull(room.getRoomVariableManager());

        assertSame(room.getGameManager(), room.getGameManager());
        assertSame(room.getGames(), room.getGames());
        assertSame(room.getCurrentHabbos(), room.getCurrentHabbos());
        assertSame(room.getHabboQueue(), room.getHabboQueue());
        assertSame(room.getFurniOwnerNames(), room.getFurniOwnerNames());
        assertSame(room.getFurniOwnerCount(), room.getFurniOwnerCount());
        assertSame(room.getRights(), room.getRights());
        assertSame(room.getMoodlightData(), room.getMoodlightData());
        assertSame(room.cache, room.cache);
        assertSame(room.userVotes, room.userVotes);
        assertSame(room.scheduledComposers, room.scheduledComposers);
        assertSame(room.scheduledTasks, room.scheduledTasks);
    }

    @Test
    void resultSetConstructorRemainsTheOnlyPublicConstructor() throws Exception {
        Constructor<Room> constructor = Room.class.getConstructor(ResultSet.class);

        assertTrue(Modifier.isPublic(constructor.getModifiers()));
        assertEquals(1, Room.class.getConstructors().length);
    }

    @Test
    void packagePrivateSeamBuildsAUsableRoomWithoutGlobalBootstrap() throws Exception {
        setEmulatorField("database", null);
        setEmulatorField("config", null);

        Room room = new Room(41, 7);

        assertEquals(41, room.getId());
        assertEquals(7, room.getOwnerId());
        assertNotNull(room.getTileManager());
        assertNotNull(room.getGameManager());
        assertNotNull(room.getTradeManager());
        assertNotNull(room.getPromotionManager());
        assertNotNull(room.getWordQuizManager());
        assertNotNull(room.getRightsManager());
        assertNotNull(room.getUnitManager());
        assertNotNull(room.getItemManager());
        assertNotNull(room.getChatManager());
        assertNotNull(room.getRollerManager());
        assertNotNull(room.getMessagingManager());
        assertNotNull(room.getCycleManager());
        assertNotNull(room.getUserVariableManager());
        assertNotNull(room.getFurniVariableManager());
        assertNotNull(room.getRoomVariableManager());
        assertSame(room.getGames(), room.getGames());
        assertSame(room.getCurrentHabbos(), room.getCurrentHabbos());
        assertSame(room.getFurniOwnerNames(), room.getFurniOwnerNames());
        assertSame(room.getFurniOwnerCount(), room.getFurniOwnerCount());
        assertSame(room.getWordFilterWords(), room.getWordFilterWords());
    }

    private static void setEmulatorField(String name, Object value) throws Exception {
        Field field = Emulator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static Database databaseUsing(HikariDataSource dataSource) throws Exception {
        Constructor<Database> constructor =
                Database.class.getDeclaredConstructor(HikariDataSource.class);
        constructor.setAccessible(true);
        return constructor.newInstance(dataSource);
    }

    private static ResultSet roomRow() {
        Map<String, Object> values = new HashMap<>();
        values.put("id", 41);
        values.put("owner_id", 7);
        values.put("owner_name", "owner");
        values.put("name", "Characterized room");
        values.put("description", "Existing constructor behavior");
        values.put("password", "secret");
        values.put("state", "password");
        values.put("users_max", 25);
        values.put("score", 88);
        values.put("category", 3);
        values.put("paper_floor", "1.1");
        values.put("paper_wall", "2.2");
        values.put("paper_landscape", "3.3");
        values.put("thickness_wall", 2);
        values.put("wall_height", -1);
        values.put("thickness_floor", 4);
        values.put("tags", "retro,polaris");
        values.put("is_public", true);
        values.put("is_staff_picked", true);
        values.put("allow_other_pets", true);
        values.put("allow_other_pets_eat", false);
        values.put("allow_walkthrough", true);
        values.put("allow_hidewall", true);
        values.put("youtube_enabled", true);
        values.put("soundboard_enabled", false);
        values.put("chat_mode", 1);
        values.put("chat_weight", 2);
        values.put("chat_speed", 3);
        values.put("chat_hearing_distance", 4);
        values.put("chat_protection", 5);
        values.put("who_can_mute", 1);
        values.put("who_can_kick", 2);
        values.put("who_can_ban", 3);
        values.put("poll_id", 19);
        values.put("guild_id", 23);
        values.put("roller_speed", 4);
        values.put("override_model", "1");
        values.put("model", "model_a");
        values.put("promoted", "1");
        values.put("jukebox_active", "1");
        values.put("hidewired", "1");
        values.put("builders_club_trial_locked", true);
        values.put("builders_club_original_state", "locked");
        values.put("trade_mode", 2);
        values.put("move_diagonally", "1");
        values.put("allow_underpass", "1");
        values.put("moodlight_data", "2,1,1,#123456,200");

        return proxy(ResultSet.class, (ignored, method, arguments) -> {
            if ("toString".equals(method.getName())) {
                return "room result-set fixture";
            }
            String column = (String) arguments[0];
            Object value = values.get(column);
            return switch (method.getName()) {
                case "getInt" -> value instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(String.valueOf(value));
                case "getBoolean" -> value instanceof Boolean flag
                        ? flag
                        : "1".equals(String.valueOf(value));
                case "getString" -> value == null ? null : String.valueOf(value);
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                handler);
    }

    private static final class RecordingDataSource extends HikariDataSource {
        private String sql;
        private final Map<Integer, Integer> parameters = new HashMap<>();

        @Override
        public Connection getConnection() {
            return proxy(Connection.class, (ignored, method, arguments) -> switch (method.getName()) {
                case "prepareStatement" -> {
                    this.sql = (String) arguments[0];
                    yield statement();
                }
                case "close" -> null;
                case "isClosed" -> false;
                case "toString" -> "room construction connection";
                default -> defaultValue(method.getReturnType());
            });
        }

        private PreparedStatement statement() {
            return proxy(PreparedStatement.class, (ignored, method, arguments) -> {
                switch (method.getName()) {
                    case "setInt" -> {
                        this.parameters.put((Integer) arguments[0], (Integer) arguments[1]);
                        return null;
                    }
                    case "executeQuery" -> {
                        return proxy(ResultSet.class, (result, resultMethod, resultArguments) ->
                                switch (resultMethod.getName()) {
                                    case "next" -> false;
                                    case "close" -> null;
                                    case "toString" -> "empty room-ban result set";
                                    default -> defaultValue(resultMethod.getReturnType());
                                });
                    }
                    case "close" -> {
                        return null;
                    }
                    case "toString" -> {
                        return "room construction statement";
                    }
                    default -> {
                        return defaultValue(method.getReturnType());
                    }
                }
            });
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }
}
