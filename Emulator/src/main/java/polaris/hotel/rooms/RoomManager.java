package polaris.hotel.rooms;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.database.SqlQueries;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.bots.Bot;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.games.Game;
import polaris.hotel.games.battlebanzai.BattleBanzaiGame;
import polaris.hotel.games.football.FootballGame;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.games.tag.BunnyrunGame;
import polaris.hotel.games.tag.IceTagGame;
import polaris.hotel.games.tag.RollerskateGame;
import polaris.hotel.games.wired.WiredGame;
import polaris.hotel.guilds.Guild;
import polaris.hotel.items.interactions.InteractionWired;
import polaris.hotel.items.interactions.wired.chest.ChestAutoLock;
import polaris.hotel.messenger.MessengerBuddy;
import polaris.hotel.navigation.NavigatorFilterComparator;
import polaris.hotel.navigation.NavigatorFilterField;
import polaris.hotel.navigation.NavigatorManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetData;
import polaris.hotel.pets.PetTasks;
import polaris.hotel.polls.Poll;
import polaris.hotel.polls.PollManager;
import polaris.hotel.rooms.raidprotection.RaidProtectionManager;
import polaris.hotel.users.DanceType;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.UserStats;
import polaris.hotel.wired.core.WiredManager;
import polaris.hotel.wired.label.WiredLabelSupport;
import polaris.hotel.wired.protocol.WiredTextPlaceholderContext;
import polaris.messages.incoming.users.UserNuxEvent;
import polaris.messages.outgoing.hotelview.HotelViewComposer;
import polaris.messages.outgoing.polls.PollStartComposer;
import polaris.messages.outgoing.polls.infobus.SimplePollAnswersComposer;
import polaris.messages.outgoing.polls.infobus.SimplePollStartComposer;
import polaris.messages.outgoing.rooms.ConfigurationItemStatesComposer;
import polaris.messages.outgoing.rooms.DoorbellAddUserComposer;
import polaris.messages.outgoing.rooms.FloodCounterComposer;
import polaris.messages.outgoing.rooms.ForwardToRoomComposer;
import polaris.messages.outgoing.rooms.HideDoorbellComposer;
import polaris.messages.outgoing.rooms.RoomDataComposer;
import polaris.messages.outgoing.rooms.RoomEnterErrorComposer;
import polaris.messages.outgoing.rooms.RoomModelComposer;
import polaris.messages.outgoing.rooms.RoomOpenComposer;
import polaris.messages.outgoing.rooms.RoomPaintComposer;
import polaris.messages.outgoing.rooms.RoomPaneComposer;
import polaris.messages.outgoing.rooms.RoomQueueStatusMessage;
import polaris.messages.outgoing.rooms.RoomScoreComposer;
import polaris.messages.outgoing.rooms.RoomThicknessComposer;
import polaris.messages.outgoing.rooms.YouAreNotSpectatorComposer;
import polaris.messages.outgoing.rooms.items.ConfInvisStateComposer;
import polaris.messages.outgoing.rooms.items.HanditemBlockStateComposer;
import polaris.messages.outgoing.rooms.items.RoomFloorItemsComposer;
import polaris.messages.outgoing.rooms.items.RoomWallItemsComposer;
import polaris.messages.outgoing.rooms.pets.RoomPetComposer;
import polaris.messages.outgoing.rooms.promotions.RoomPromotionMessageComposer;
import polaris.messages.outgoing.rooms.users.RoomUnitIdleComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDanceComposer;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;
import polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer;
import polaris.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import polaris.messages.outgoing.rooms.users.RoomUserPetComposer;
import polaris.messages.outgoing.rooms.users.RoomUserRemoveComposer;
import polaris.messages.outgoing.rooms.users.RoomUserStatusComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersAddGuildBadgeComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersComposer;
import polaris.messages.outgoing.rooms.users.RoomUsersGuildBadgesComposer;
import polaris.messages.outgoing.users.MutedWhisperComposer;
import polaris.messages.outgoing.users.UserBadgesComposer;
import polaris.messages.outgoing.wired.WiredEnvironmentComposer;
import polaris.plugin.events.navigator.NavigatorRoomCreatedEvent;
import polaris.plugin.events.rooms.RoomFloorItemsLoadEvent;
import polaris.plugin.events.rooms.RoomUncachedEvent;
import polaris.plugin.events.rooms.UserVoteRoomEvent;
import polaris.plugin.events.users.UserAddedToRoomEvent;
import polaris.plugin.events.users.UserEnterRoomEvent;
import polaris.plugin.events.users.UserExitRoomEvent;

public class RoomManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomManager.class);
    private static final String PUBLIC_ROOMS_SQL = """
            SELECT id, owner_id, owner_name, name, description, password, state,
                   users_max, score, category, paper_floor, paper_wall,
                   paper_landscape, thickness_wall, wall_height, thickness_floor,
                   tags, is_public, is_staff_picked, allow_other_pets,
                   allow_other_pets_eat, allow_walkthrough, allow_hidewall,
                   youtube_enabled, soundboard_enabled, chat_mode, chat_weight,
                   chat_speed, chat_hearing_distance, chat_protection,
                   who_can_mute, who_can_kick, who_can_ban, poll_id, guild_id,
                   roller_speed, override_model, model, promoted, jukebox_active,
                   hidewired, builders_club_trial_locked,
                   builders_club_original_state, trade_mode, move_diagonally,
                   allow_underpass, mute_all_pets, leave_on_door_tile,
                   idle_sleep_enabled, idle_sleep_timeout_seconds,
                   idle_autokick_enabled, idle_autokick_timeout_seconds,
                   moodlight_data, date_created
            FROM rooms
            WHERE is_public = ? OR is_staff_picked = ? ORDER BY id DESC
            """;
    private static final String ROOM_IDS_BY_OWNER_NAME_SQL = """
            SELECT id
            FROM rooms
            WHERE owner_name = ? ORDER BY id DESC LIMIT 25
            """;
    private static final String ROOMS_BY_OWNER_ID_SQL = """
            SELECT id, owner_id, owner_name, name, description, password, state,
                   users_max, score, category, paper_floor, paper_wall,
                   paper_landscape, thickness_wall, wall_height, thickness_floor,
                   tags, is_public, is_staff_picked, allow_other_pets,
                   allow_other_pets_eat, allow_walkthrough, allow_hidewall,
                   youtube_enabled, soundboard_enabled, chat_mode, chat_weight,
                   chat_speed, chat_hearing_distance, chat_protection,
                   who_can_mute, who_can_kick, who_can_ban, poll_id, guild_id,
                   roller_speed, override_model, model, promoted, jukebox_active,
                   hidewired, builders_club_trial_locked,
                   builders_club_original_state, trade_mode, move_diagonally,
                   allow_underpass, mute_all_pets, leave_on_door_tile,
                   idle_sleep_enabled, idle_sleep_timeout_seconds,
                   idle_autokick_enabled, idle_autokick_timeout_seconds,
                   moodlight_data, date_created
            FROM rooms
            WHERE owner_id = ?
            """;

    private static final int page = 0;
    // Configuration. Loaded from database & updated accordingly.
    public static volatile int MAXIMUM_ROOMS_USER = 25;
    public static volatile int MAXIMUM_ROOMS_HC = 35;
    public static volatile int HOME_ROOM_ID = 0;
    public static volatile boolean SHOW_PUBLIC_IN_POPULAR_TAB = false;
    private final Map<Integer, RoomCategory> roomCategories;
    private final RoomModelRepository roomModelRepository;
    private final RoomRepository roomRepository;
    private final List<String> mapNames;
    private final ConcurrentHashMap<String, RoomLayoutData> layoutCache;
    private final RoomDirectory roomDirectory;
    private final RoomSearchService roomSearchService;
    private final RoomModerationService roomModerationService;
    private final RoomLifecycleService roomLifecycleService;
    private final RoomEntryService roomEntryService;
    private final ConcurrentHashMap<Integer, Room> activeRooms;
    private final ConcurrentHashMap<Integer, Set<Integer>> roomsByOwner;
    private final AtomicInteger indexedRoomCount;
    private final ArrayList<Class<? extends Game>> gameTypes;
    private final Executor persistenceExecutor;

    public RoomManager() {
        this(true, Runnable::run);
    }

    public RoomManager(Executor persistenceExecutor) {
        this(true, persistenceExecutor);
    }

    RoomManager(boolean initialize) {
        this(initialize, Runnable::run);
    }

    RoomManager(boolean initialize, Executor persistenceExecutor) {
        long millis = System.currentTimeMillis();
        this.persistenceExecutor = Objects.requireNonNull(persistenceExecutor, "persistenceExecutor");
        this.roomCategories = new HashMap<>();
        this.roomRepository = new RoomRepository(this::openConnection);
        this.roomModelRepository = new RoomModelRepository(this::openConnection);
        this.mapNames = this.roomModelRepository.modelNames();
        this.layoutCache = this.roomModelRepository.layouts();
        this.roomDirectory = new RoomDirectory();
        this.activeRooms = this.roomDirectory.activeRooms();
        this.roomsByOwner = this.roomDirectory.roomsByOwner();
        this.indexedRoomCount = this.roomDirectory.indexedRoomCount();
        this.roomSearchService = new RoomSearchService(this.activeRooms::values, Duration.ofSeconds(1));
        this.roomLifecycleService = new RoomLifecycleService(
                this.roomDirectory,
                this.roomCategories,
                ownerId -> Emulator.getGameServer().getGameClientManager().containsUser(ownerId),
                room -> !Emulator.getPluginManager()
                        .fireEvent(new RoomUncachedEvent(room))
                        .isCancelled(),
                this.roomSearchService::invalidate);
        this.roomModerationService = new RoomModerationService(
                this::getRoom,
                userId -> Emulator.getGameEnvironment().getUserManager().getUser(userId),
                UserManager::getOfflineUserInfo,
                () -> Emulator.getIntUnixTimestamp(),
                RoomBan::insert);
        this.roomEntryService = new RoomEntryService(
                roomId -> this.loadRoom(roomId),
                Room::loadAccessData,
                this::ensureRoomDataLoaded,
                this::getRoom,
                (user, room) -> !Emulator.getPluginManager()
                        .fireEvent(new UserEnterRoomEvent(user, room))
                        .isCancelled(),
                this::openRoom);

        this.gameTypes = new ArrayList<>();

        if (initialize) {
            this.loadRoomCategories();
            this.loadRoomModels();

            registerGameType(BattleBanzaiGame.class);
            registerGameType(FreezeGame.class);
            registerGameType(WiredGame.class);
            registerGameType(FootballGame.class);
            registerGameType(BunnyrunGame.class);
            registerGameType(IceTagGame.class);
            registerGameType(RollerskateGame.class);

            LOGGER.info("Room Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
        }
    }

    private RoomDependencies roomDependencies() {
        return new RoomDependencies(this::openConnection, this.persistenceExecutor::execute);
    }

    private Connection openConnection() throws SQLException {
        return Emulator.getDatabase().getDataSource().getConnection();
    }

    void registerActiveRoom(Room room) {
        this.roomDirectory.register(room);
        this.roomSearchService.invalidate();
    }

    /** @see RoomDirectory#registerIfAbsent(Room) */
    Room registerActiveRoomIfAbsent(Room room) {
        Room active = this.roomDirectory.registerIfAbsent(room);
        if (active == room) {
            this.roomSearchService.invalidate();
        }
        return active;
    }

    public void loadRoomModels() {
        this.roomModelRepository.reload();
    }

    public CustomRoomLayout loadCustomLayout(Room room) {
        return this.roomModelRepository.loadCustomLayout(room);
    }

    private void loadRoomCategories() {
        this.roomCategories.clear();

        try (Connection connection = this.openConnection();
                Statement statement = connection.createStatement();
                ResultSet set = statement.executeQuery("SELECT * FROM navigator_flatcats")) {
            while (set.next()) {
                this.roomCategories.put(set.getInt("id"), new RoomCategory(set));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void loadPublicRooms() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(PUBLIC_ROOMS_SQL)) {
            statement.setString(1, "1");
            statement.setString(2, "1");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Room room = new Room(set, this.roomDependencies());
                    room.preventUncaching = true;
                    this.registerActiveRoom(room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public Map<Integer, List<Room>> findRooms(
            NavigatorFilterField filterField, String value, int category, boolean showInvisible) {
        Map<Integer, List<Room>> rooms = new HashMap<>();
        String query = filterField.databaseQuery + " AND rooms.state NOT LIKE " + (showInvisible ? "''" : "'invisible'")
                + (category >= 0 ? "AND rooms.category = '" + category + "'" : "")
                + "  ORDER BY rooms.users, rooms.id DESC LIMIT " + (page * NavigatorManager.MAXIMUM_RESULTS_PER_PAGE)
                + "" + ((page * NavigatorManager.MAXIMUM_RESULTS_PER_PAGE) + NavigatorManager.MAXIMUM_RESULTS_PER_PAGE);
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(
                    1, (filterField.comparator == NavigatorFilterComparator.EQUALS ? value : "%" + value + "%"));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Room room = this.activeRooms.get(set.getInt("id"));

                    if (room == null) {
                        room = new Room(set, this.roomDependencies());
                        this.registerActiveRoom(room);
                    }

                    if (!rooms.containsKey(set.getInt("category"))) {
                        rooms.put(set.getInt("category"), new ArrayList<>());
                    }

                    rooms.get(set.getInt("category")).add(room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public RoomCategory getCategory(int id) {
        return this.roomCategories.get(id);
    }

    public RoomCategory getCategory(String name) {
        for (RoomCategory category : this.roomCategories.values()) {
            if (category.getCaption().equalsIgnoreCase(name)) {
                return category;
            }
        }

        return null;
    }

    public RoomCategory getCategoryBySafeCaption(String safeCaption) {
        for (RoomCategory category : this.roomCategories.values()) {
            if (category.getCaptionSave().equalsIgnoreCase(safeCaption)) {
                return category;
            }
        }

        return null;
    }

    public List<RoomCategory> roomCategoriesForUser(User user) {
        List<RoomCategory> categories = new ArrayList<>();
        for (RoomCategory category : this.roomCategories.values()) {
            if (category.getMinRank() <= user.getUserInfo().getRank().getId()) categories.add(category);
        }

        Collections.sort(categories);

        return categories;
    }

    public boolean hasCategory(int categoryId, User user) {
        RoomCategory category = this.roomCategories.get(categoryId);
        return category != null
                && category.getMinRank() <= user.getUserInfo().getRank().getId();
    }

    public Map<Integer, RoomCategory> getRoomCategories() {
        return this.roomCategories;
    }

    public List<Room> getRoomsByScore() {
        List<Room> rooms = new ArrayList<>(this.activeRooms.values());
        rooms.sort(Room.SORT_SCORE);

        return rooms;
    }

    public List<Room> getActiveRooms(int categoryId) {
        List<Room> rooms = new ArrayList<>();
        for (Room room : this.activeRooms.values()) {
            if (categoryId == room.getCategory() || categoryId == -1) rooms.add(room);
        }
        Collections.sort(rooms);
        return rooms;
    }

    // TODO Move to UserInfo class.
    public List<Room> getRoomsForUser(User user) {
        List<Room> rooms = new ArrayList<>();
        for (Room room : this.activeRooms.values()) {
            if (room.getOwnerId() == user.getUserInfo().getId()) rooms.add(room);
        }
        rooms.sort(Room.SORT_ID);
        return rooms;
    }

    public List<Room> getOwnedRoomsForUser(User user) {
        List<Room> rooms = new ArrayList<>();
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(ROOMS_BY_OWNER_ID_SQL)) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) {
                        rooms.add(this.activeRooms.get(set.getInt("id")));
                    } else {
                        rooms.add(new Room(set, this.roomDependencies()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        rooms.sort(Room.SORT_ID);
        return rooms;
    }

    public List<Room> getRoomsForUser(String username) {
        User h = Emulator.getGameEnvironment().getUserManager().getUser(username);
        if (h != null) {
            return this.getRoomsForUser(h);
        }

        List<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(ROOM_IDS_BY_OWNER_NAME_SQL)) {
            statement.setString(1, username);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    rooms.add(this.loadRoom(set.getInt("id")));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public Room loadRoom(int id) {
        return loadRoom(id, false);
    }

    /**
     * Loads a room, optionally loading its data.
     * If the room is already being loaded in the background, this will wait for that to complete.
     *
     * @param id The room ID
     * @param loadData Whether to load room data (items, bots, pets, etc.)
     * @return The loaded room, or null if not found
     */
    public Room loadRoom(int id, boolean loadData) {
        Room room = null;

        if (id == 0) {
            return null;
        }

        if (this.activeRooms.containsKey(id)) {
            room = this.activeRooms.get(id);

            if (loadData) {
                this.ensureRoomDataLoaded(room);
            }

            return room;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM rooms WHERE id = ? LIMIT 1")) {
            statement.setInt(1, id);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    room = new Room(set, this.roomDependencies());
                }
            }

            if (room != null) {
                // Register before the contents are loaded, and let the first registration
                // win: two entries arriving together used to build two Room objects for
                // one id, with the loser left ticking and saving state nobody could see.
                Room active = this.registerActiveRoomIfAbsent(room);
                if (active != room) {
                    room = active;
                }
                if (loadData) {
                    this.ensureRoomDataLoaded(room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return room;
    }

    /**
     * Whether a room info request may start loading the room contents early: only when the user
     * can walk in without password, doorbell or rights check, so a refused entry loads nothing.
     */
    public static boolean mayPrefetchRoomData(User user, Room room) {
        if (user == null || room == null || room.isBanned(user)) {
            return false;
        }
        if (room.isOwner(user) || user.hasPermission(Permission.ACC_ENTERANYROOM)) {
            return true;
        }
        return room.getState() == RoomState.OPEN && !room.isBuildersClubTrialLocked();
    }

    /** Loads the room contents unless they are loaded already; waits for a load in progress. */
    void ensureRoomDataLoaded(Room room) {
        if (room.isLoadingInProgress()) {
            // Wait for background loading to complete
            room.waitForLoad();
        } else if (room.isPreLoaded() && !room.isLoaded()) {
            room.loadData();
        }
    }

    public Room createRoom(
            int ownerId,
            String ownerName,
            String name,
            String description,
            String modelName,
            int usersMax,
            int categoryId,
            int tradeType) {
        Room room = null;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO rooms (owner_id, owner_name, name, description, model, users_max, category, trade_mode,"
                                + " date_created) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, ownerId);
            statement.setString(2, ownerName);
            statement.setString(3, name);
            statement.setString(4, description);
            statement.setString(5, modelName);
            statement.setInt(6, usersMax);
            statement.setInt(7, categoryId);
            statement.setInt(8, tradeType);
            // Recorded from now on; rooms made before this column existed keep the zero it
            // defaults to, which the room competition reads as "age unknown".
            statement.setInt(9, Emulator.getIntUnixTimestamp());
            statement.execute();
            try (ResultSet set = statement.getGeneratedKeys()) {
                if (set.next()) room = this.loadRoom(set.getInt(1));
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return room;
    }

    public Room createRoomForUser(
            User user, String name, String description, String modelName, int usersMax, int categoryId, int tradeType) {
        Room room = this.createRoom(
                user.getUserInfo().getId(),
                user.getUserInfo().getUsername(),
                name,
                description,
                modelName,
                usersMax,
                categoryId,
                tradeType);

        Emulator.getPluginManager().fireEvent(new NavigatorRoomCreatedEvent(user, room));
        if (room != null) {
            polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.CREATE_ROOM, 1);
        }

        return room;
    }

    public void loadRoomsForUser(User user) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(ROOMS_BY_OWNER_ID_SQL)) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (!this.activeRooms.containsKey(set.getInt("id"))) {
                        Room room = new Room(set, this.roomDependencies());
                        this.registerActiveRoom(room);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void unloadRoomsForUser(User user) {
        this.roomLifecycleService.unloadRoomsFor(user);
    }

    List<Room> roomsToUnloadForOwner(int ownerId) {
        return this.roomLifecycleService.roomsToUnloadForOwner(ownerId);
    }

    public void clearInactiveRooms() {
        this.roomLifecycleService.clearInactiveRooms();
    }

    public boolean layoutExists(String name) {
        return this.roomModelRepository.exists(name);
    }

    /** room_models.club_only: only Habbo Club members may create a room with it. */
    public boolean layoutClubOnly(String name) {
        RoomLayoutData layout = this.roomModelRepository.layouts().get(name);
        return layout != null && layout.clubOnly;
    }

    public RoomLayout loadLayout(String name, Room room) {
        return this.roomModelRepository.load(name, room);
    }

    public void unloadRoom(Room room) {
        this.roomLifecycleService.unload(room);
    }

    public void uncacheRoom(Room room) {
        this.roomLifecycleService.uncache(room);
    }

    public void voteForRoom(User user, Room room) {
        if (user.getUserInfo().getCurrentRoom() != null
                && room != null
                && user.getUserInfo().getCurrentRoom() == room) {
            if (this.hasVotedForRoom(user, room)) return;

            UserVoteRoomEvent event = new UserVoteRoomEvent(room, user);
            if (Emulator.getPluginManager().fireEvent(event).isCancelled()) return;

            room.setScore(room.getScore() + 1);
            room.setNeedsUpdate(true);
            user.getUserStats().votedRooms.add(room.getId());
            for (User h : room.getUsers()) {
                h.getClient().sendResponse(new RoomScoreComposer(room.getScore(), !this.hasVotedForRoom(h, room)));
            }

            try {
                this.roomRepository.recordVote(room.getId(), user.getUserInfo().getId());
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    boolean hasVotedForRoom(User user, Room room) {
        if (room.getOwnerId() == user.getUserInfo().getId()) return true;

        for (int i : user.getUserStats().votedRooms) {
            if (i == room.getId()) return true;
        }

        return false;
    }

    public Room getRoom(int roomId) {
        return this.activeRooms.get(roomId);
    }

    public ArrayList<Room> getActiveRooms() {
        return new ArrayList<>(this.activeRooms.values());
    }

    public int loadedRoomsCount() {
        return this.activeRooms.size();
    }

    public void enterRoom(User user, int roomId, String password) {
        this.enterRoom(user, roomId, password, false, null, false);
    }

    public void enterRoom(User user, int roomId, String password, boolean overrideChecks) {
        this.enterRoom(user, roomId, password, overrideChecks, null, false);
    }

    public void enterRoom(User user, int roomId, String password, boolean overrideChecks, RoomTile doorLocation) {
        this.enterRoom(user, roomId, password, overrideChecks, doorLocation, false);
    }

    public void enterRoom(
            User user,
            int roomId,
            String password,
            boolean overrideChecks,
            RoomTile doorLocation,
            boolean isReconnectSpawn) {
        this.roomEntryService.enter(user, roomId, password, overrideChecks, doorLocation, isReconnectSpawn);
    }

    /**
     * Enters a room at reconnect coordinates. They are checked against the layout once the
     * contents are loaded (a walkable tile, or the door otherwise); negative means the door.
     */
    public void enterRoomAt(User user, int roomId, String password, int spawnX, int spawnY) {
        this.roomEntryService.enter(user, roomId, password, false, RoomEntryService.Spawn.reconnectAt(spawnX, spawnY));
    }

    void openRoom(User user, Room room, RoomTile doorLocation) {
        this.openRoom(user, room, doorLocation, false);
    }

    void openRoom(User user, Room room, RoomTile doorLocation, boolean isReconnectSpawn) {
        if (room == null || room.getLayout() == null) return;

        this.markRoomOpened(user, room);
        // Decided before the entry log records the room as visited.
        boolean firstVisit = markRoomVisited(user.getUserStats(), room.getId());
        if (Emulator.getConfig().getBoolean("hotel.room.enter.logs")) {
            this.logEnter(user, room);
        }

        if (user.getUserInfo().getRoomQueueId() > 0) {
            Room r = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoom(user.getUserInfo().getRoomQueueId());

            if (r != null) {
                r.removeFromQueue(user);
            }
        }

        user.getUserInfo().setRoomQueueId(0);
        user.getClient().sendResponse(new HideDoorbellComposer(room.getId(), ""));

        if (user.getRoomUnit() != null) {
            RoomUnit existingRoom = user.getRoomUnit();
            if (existingRoom.getRoom() != null) {
                if (existingRoom.getCurrentLocation() != null)
                    existingRoom.getCurrentLocation().removeUnit(existingRoom);
                existingRoom.getRoom().sendComposer(new RoomUserRemoveComposer(existingRoom).compose());
            }
            user.getRoomUnit().setRoom(null);
        }

        user.setRoomUnit(new RoomUnit());
        user.getUserInfo().setRoomEntryMethod("door");
        user.getUserInfo().setRoomEntryTeleportId(0);

        user.getRoomUnit().clearStatus();
        if (user.getRoomUnit().getCurrentLocation() == null) {
            user.getRoomUnit()
                    .setLocation(
                            doorLocation != null
                                    ? doorLocation
                                    : room.getLayout().getDoorTile());
            if (user.getRoomUnit().getCurrentLocation() != null)
                user.getRoomUnit().setZ(user.getRoomUnit().getCurrentLocation().getStackHeight());

            if (doorLocation == null) {
                user.getRoomUnit()
                        .setBodyRotation(
                                RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
                user.getRoomUnit()
                        .setHeadRotation(
                                RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
            } else if (isReconnectSpawn) {
                // Reconnect spawn: place at tile but keep normal room behavior
                // (user can still leave by door, no teleport flags)
                user.getRoomUnit()
                        .setBodyRotation(
                                RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
                user.getRoomUnit()
                        .setHeadRotation(
                                RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
            } else {
                // Furniture teleport spawn
                user.getRoomUnit().setCanLeaveRoomByDoor(false);
                user.getRoomUnit().isTeleporting = true;
                user.getUserInfo().setRoomEntryMethod("teleport");
                UserItem topItem = room.getTopItemAt(doorLocation.x, doorLocation.y);
                if (topItem != null) {
                    user.getUserInfo().setRoomEntryTeleportId(topItem.getId());
                    user.getRoomUnit().setRotation(RoomUserRotation.values()[topItem.getRotation()]);
                }
            }
        }
        PendingRoomEntrySupport.apply(user, room, doorLocation == null, System.currentTimeMillis());

        user.getRoomUnit().setRoomUnitType(RoomUnitType.USER);
        if (room.isBanned(user)) {
            user.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
            return;
        }

        // AIR raid protection: a raider is kicked or banned at the door, the guard keeps suspicious visitors out.
        if (!RaidProtectionManager.get().admit(room, user, firstVisit)) {
            return;
        }

        if (this.queuedBecauseFull(user, room)) {
            return;
        }

        user.getRoomUnit().clearStatus();
        user.getRoomUnit().cmdTeleport = false;

        user.getClient().sendResponse(new RoomOpenComposer());

        user.getRoomUnit().setInRoom(true);
        if (user.getUserInfo().getCurrentRoom() != room && user.getUserInfo().getCurrentRoom() != null) {
            user.getUserInfo().getCurrentRoom().removeUser(user, true);
        } else if (!user.getUserStats().blockFollowing && user.getUserInfo().getCurrentRoom() == null) {
            user.getMessenger().connectionChanged(user, true, true);
        }

        if (user.getUserInfo().getLoadingRoom() != 0) {
            Room oldRoom = Emulator.getGameEnvironment()
                    .getRoomManager()
                    .getRoom(user.getUserInfo().getLoadingRoom());
            if (oldRoom != null) {
                oldRoom.removeFromQueue(user);
            }
        }

        user.getUserInfo().setLoadingRoom(room.getId());

        user.getClient().sendResponse(new RoomModelComposer(room));

        if (room.getWallPaint() != null && !room.getWallPaint().equals("0.0"))
            user.getClient().sendResponse(new RoomPaintComposer("wallpaper", room.getWallPaint()));

        if (room.getFloorPaint() != null && !room.getFloorPaint().equals("0.0"))
            user.getClient().sendResponse(new RoomPaintComposer("floor", room.getFloorPaint()));

        user.getClient().sendResponse(new RoomPaintComposer("landscape", room.getBackgroundPaint()));

        room.refreshRightsForUser(user);

        user.getClient().sendResponse(new RoomScoreComposer(room.getScore(), !this.hasVotedForRoom(user, room)));

        user.getRoomUnit()
                .setFastWalk(
                        user.getRoomUnit().isFastWalk() && user.hasPermission("cmd_fastwalk", room.hasRights(user)));

        if (room.isPromoted()) {
            user.getClient().sendResponse(new RoomPromotionMessageComposer(room, room.getPromotion()));
        } else {
            user.getClient().sendResponse(new RoomPromotionMessageComposer(null, null));
        }

        if (room.getOwnerId() != user.getUserInfo().getId() && firstVisit) {
            AchievementManager.progressAchievement(
                    user, Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomEntry"));
        }
    }

    public void enterRoom(final User user, final Room room) {
        // Checked again here: the client asks for the heightmap a round trip after the room was opened,
        // and every visitor who opened a room with one free place in the meantime would otherwise enter.
        if (user.getUserInfo().getLoadingRoom() == room.getId() && this.queuedBecauseFull(user, room)) {
            return;
        }

        if (!this.placeAtDoor(user, room)) {
            return;
        }

        user.getRoomUnit().setPathFinderRoom(room);
        user.getRoomUnit().resetIdleTimer();

        user.getRoomUnit().setInvisible(false);
        room.addUser(user);
        BuildersClubRoomSupport.sendCurrentRoomPlacementStatus(room);
        room.getUserVariableManager().restorePermanentAssignments(user);

        user.getClient()
                .sendResponse(new UserBadgesComposer(
                        user.getInventory().getBadgesComponent().getWearingBadges(),
                        user.getUserInfo().getId()));

        List<User> users = this.announceEntry(user, room);

        this.sendRoomState(user, room);
        this.sendPetsAndMuteState(user, room);
        this.sendOtherUsersState(user, users);
        this.sendRoomExtras(user, room);

        WiredManager.triggerUserEntersRoom(room, user.getRoomUnit());
        room.userEntered(user);
        if (room.getOwnerId() != user.getUserInfo().getId()) {
            polaris.hotel.quests.QuestProgressEvents.progress(user, polaris.hotel.quests.QuestGoalType.VISIT_ROOMS, 1);
        }

        if (!user.getUserStats().nux && (room.isOwner(user) || room.isPublicRoom())) {
            UserNuxEvent.handle(user);
        }
    }

    /**
     * A full room queues the visitor (AIR 13 RoomQueueStatus, our 2208) instead of bouncing it with
     * CantConnect(REASON_FULL); staff with the full-rooms permission and users with rights always enter.
     *
     * @return true when the visitor was queued and sent to the hotel view
     */
    boolean queuedBecauseFull(final User user, final Room room) {
        if (room.getUserCount() < room.getUsersMax()
                || user.hasPermission(Permission.ACC_FULLROOMS)
                || room.hasRights(user)) {
            return false;
        }

        RoomVisitorQueueSupport.enqueue(room, user);
        user.getUserInfo().setLoadingRoom(0);
        user.getClient().sendResponse(new HotelViewComposer());
        return true;
    }

    /** Puts the entering user on the door tile of the room; false when the room has no usable door and the user went back to the hotel view. */
    private boolean placeAtDoor(final User user, final Room room) {
        if (user.getUserInfo().getLoadingRoom() != room.getId()) {
            if (user.getUserInfo().getLoadingRoom() != 0) {
                user.getClient().sendResponse(new HotelViewComposer());
            }
            return false;
        }

        user.getRoomUnit().removeStatus(RoomUnitStatus.FLAT_CONTROL);
        user.getUserInfo().setLoadingRoom(0);
        user.getUserInfo().setCurrentRoom(room);
        user.getRoomUnit().setPathFinderRoom(room);
        user.getRoomUnit().setHandItem(0);

        user.getRoomUnit().setRightsLevel(RoomRightLevels.NONE);
        room.refreshRightsForUser(user);
        if (user.getRoomUnit().isKicked && !user.getRoomUnit().canWalk()) {
            user.getRoomUnit().setCanWalk(true);
        }
        user.getRoomUnit().isKicked = false;

        if (user.getRoomUnit().getCurrentLocation() == null && !user.getRoomUnit().isTeleporting) {
            RoomTile doorTile = room.getLayout()
                    .getTile(room.getLayout().getDoorX(), room.getLayout().getDoorY());

            if (doorTile != null) {
                user.getRoomUnit().setLocation(doorTile);
                user.getRoomUnit().setZ(doorTile.getStackHeight());
            }

            user.getRoomUnit()
                    .setBodyRotation(RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
            user.getRoomUnit()
                    .setHeadRotation(RoomUserRotation.values()[room.getLayout().getDoorDirection()]);
        }

        if (user.getRoomUnit().getCurrentLocation() == null) {
            LOGGER.warn(
                    "Failed to resolve a valid door tile for room {} ({}) while {} was entering; sending user back to hotel view",
                    room.getId(),
                    room.getName(),
                    user.getUserInfo().getUsername());
            user.getUserInfo().setLoadingRoom(0);
            user.getUserInfo().setCurrentRoom(null);
            user.getClient().sendResponse(new HotelViewComposer());
            return false;
        }

        return true;
    }

    /** Tells the users already in the room about the newcomer, and gives back the visible ones for the newcomer's own view. */
    private List<User> announceEntry(final User user, final Room room) {
        List<User> users = new ArrayList<>();
        if (!room.getCurrentUsers().isEmpty()) {

            Collection<User> usersToSendEnter = room.getCurrentUsers().values();
            Collection<User> visibleUsers = room.getUsers();

            if (Emulator.getPluginManager().isRegistered(UserAddedToRoomEvent.class, false)) {
                UserAddedToRoomEvent event = Emulator.getPluginManager()
                        .fireEvent(new UserAddedToRoomEvent(user, room, usersToSendEnter, visibleUsers));
                usersToSendEnter = event.usersToSendEnter;
                visibleUsers = event.visibleUsers;
            }

            // The newcomer is already in the room: its own entry and status come once, with the others below.
            for (User userToSendEnter : usersToSendEnter) {
                if (userToSendEnter == user) continue;
                GameClient client = userToSendEnter.getClient();
                if (client != null) {
                    client.sendResponse(new RoomUsersComposer(user).compose());
                    client.sendResponse(new RoomUserStatusComposer(user.getRoomUnit()).compose());
                }
            }

            for (User h : visibleUsers) {
                if (!h.getRoomUnit().isInvisible()) {
                    users.add(h);
                }
            }

            synchronized (room.roomUnitLock) {
                user.getClient().sendResponse(new RoomUsersComposer(users));
                user.getClient().sendResponse(new RoomUserStatusComposer(users));
            }

            if (user.getUserStats().guild != 0) {
                Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(user.getUserStats().guild);

                if (guild != null) {
                    room.sendComposer(new RoomUsersAddGuildBadgeComposer(guild).compose());
                }
            }

            int effect = user.getInventory().getEffectsComponent().activatedEffect;
            room.giveEffect(user.getRoomUnit(), effect, -1);
        }

        return users;
    }

    /** What the newcomer is sent about the room itself: bots, panes, items, the visualization state. */
    private void sendRoomState(final User user, final Room room) {
        RoomForwardGlow.onEnter(room, user);

        user.getClient()
                .sendResponse(new RoomUsersComposer(room.getCurrentBots().values(), true));
        if (!room.getCurrentBots().isEmpty()) {
            for (Bot bot : room.getCurrentBots().values()) {
                if (!bot.getRoomUnit().getDanceType().equals(DanceType.NONE)) {
                    user.getClient().sendResponse(new RoomUserDanceComposer(bot.getRoomUnit()));
                }

                user.getClient()
                        .sendResponse(new RoomUserStatusComposer(
                                bot.getRoomUnit(), bot.getRoomUnit().getZ()));
            }
        }

        user.getClient().sendResponse(new RoomPaneComposer(room, room.isOwner(user)));

        user.getClient().sendResponse(new RoomThicknessComposer(room));

        user.getClient()
                .sendResponse(new RoomDataComposer(room, user.getClient().getUser(), false, true));

        user.getClient().sendResponse(new RoomWallItemsComposer(room));
        {
            final Set<UserItem> floorItems = new HashSet<>();

            Set<UserItem> allFloorItems = new HashSet<>(room.getFloorItems());

            if (Emulator.getPluginManager().isRegistered(RoomFloorItemsLoadEvent.class, true)) {
                RoomFloorItemsLoadEvent roomFloorItemsLoadEvent =
                        Emulator.getPluginManager().fireEvent(new RoomFloorItemsLoadEvent(user, allFloorItems));
                if (roomFloorItemsLoadEvent.hasChangedFloorItems()) {
                    allFloorItems = roomFloorItemsLoadEvent.getFloorItems();
                }
            }

            final boolean hideWiredControllerActive = RoomHideWiredSupport.isActive(room);

            for (UserItem object : allFloorItems) {
                if ((room.isHideWired() || hideWiredControllerActive) && object instanceof InteractionWired) {
                    continue;
                }

                floorItems.add(object);
                if (floorItems.size() == 250) {
                    user.getClient().sendResponse(new RoomFloorItemsComposer(room.getFurniOwnerNames(), floorItems));
                    floorItems.clear();
                }
            }

            user.getClient().sendResponse(new RoomFloorItemsComposer(room.getFurniOwnerNames(), floorItems));
            floorItems.clear();
        }

        user.getClient().sendResponse(new ConfInvisStateComposer(room).compose());
        RoomAreaHideSupport.sendState(room, user.getClient());
        user.getClient().sendResponse(new HanditemBlockStateComposer(room).compose());
        // AIR 13 ConfigurationItemStates (1508): two of the four flags are per
        // viewer, so it is sent per client on entry rather than broadcast.
        user.getClient().sendResponse(new ConfigurationItemStatesComposer(room, user).compose());
        // AIR 13 WiredEnvironment (347): tells the client whether avatar clicks go through the
        // server and which achievements this room's wired can hand out.
        user.getClient().sendResponse(new WiredEnvironmentComposer(room).compose());
        // AIR furniture labels (2237): the wf_label notes this player may see.
        WiredLabelSupport.sendAll(room, user);
        // AIR wired visualization state (1826 furni, 1454 avatars): the highlights and avatar
        // transparency this player sees, applied without easing.
        WiredVisualizationBroadcaster.sendState(room, user);
        // AIR: a room whose message boxes use $(trigger_text) says so to everyone entering.
        WiredTextPlaceholderContext.sendTriggerTextNotice(room, user);
    }

    /** The pets in the room and whether the newcomer is muted. */
    private void sendPetsAndMuteState(final User user, final Room room) {
        if (!room.getCurrentPets().isEmpty()) {
            user.getClient()
                    .sendResponse(new RoomPetComposer(room.getCurrentPets().values()));
            for (Pet pet : room.getCurrentPets().values()) {
                user.getClient().sendResponse(new RoomUserStatusComposer(pet.getRoomUnit()));
            }
        }

        if (!user.getUserStats().allowTalk()) {
            user.getUserStats().mutedBubbleTracker = true;
            int remainingMuteTime = user.getUserStats().remainingMuteTime();
            user.getClient().sendResponse(new FloodCounterComposer(remainingMuteTime));
            user.getClient().sendResponse(new MutedWhisperComposer(remainingMuteTime));
        } else if (user.getUserStats().mutedBubbleTracker) {
            user.getUserStats().mutedBubbleTracker = false;
        }
    }

    /** What the other users are doing: dancing, hand items, effects, ignores, guild badges and pets. */
    private void sendOtherUsersState(final User user, final List<User> users) {
        Map<Integer, String> guildBadges = new HashMap<>();
        for (User roomUser : users) {
            {
                if (roomUser.getRoomUnit().getDanceType().getType() > 0) {
                    user.getClient().sendResponse(new RoomUserDanceComposer(roomUser.getRoomUnit()));
                }

                if (roomUser.getRoomUnit().getHandItem() > 0) {
                    user.getClient().sendResponse(new RoomUserHandItemComposer(roomUser.getRoomUnit()));
                }

                if (roomUser.getRoomUnit().getEffectId() > 0) {
                    user.getClient().sendResponse(new RoomUserEffectComposer(roomUser.getRoomUnit()));
                }

                if (roomUser.getRoomUnit().isIdle()) {
                    user.getClient().sendResponse(new RoomUnitIdleComposer(roomUser.getRoomUnit()));
                }

                if (roomUser.getUserStats().userIgnored(user.getUserInfo().getId())) {
                    roomUser.getClient()
                            .sendResponse(new RoomUserIgnoredComposer(user, RoomUserIgnoredComposer.IGNORED));
                }

                // Only the entering user's own ignores; another user's mute is not theirs to see.
                if (user.getUserStats().userIgnored(roomUser.getUserInfo().getId())) {
                    user.getClient()
                            .sendResponse(new RoomUserIgnoredComposer(roomUser, RoomUserIgnoredComposer.IGNORED));
                }

                if (roomUser.getUserStats().guild != 0 && !guildBadges.containsKey(roomUser.getUserStats().guild)) {
                    Guild guild =
                            Emulator.getGameEnvironment().getGuildManager().getGuild(roomUser.getUserStats().guild);

                    if (guild != null) {
                        guildBadges.put(roomUser.getUserStats().guild, guild.getBadge());
                    }
                }

                if (roomUser.getRoomUnit().getRoomUnitType().equals(RoomUnitType.PET)) {
                    try {
                        user.getClient().sendResponse(new RoomUserRemoveComposer(roomUser.getRoomUnit()));
                        user.getClient()
                                .sendResponse(new RoomUserPetComposer(
                                        ((PetData) roomUser.getUserStats().cache.get("pet_type")).getType(),
                                        (Integer) roomUser.getUserStats().cache.get("pet_race"),
                                        (String) roomUser.getUserStats().cache.get("pet_color"),
                                        roomUser));
                    } catch (Exception e) {

                    }
                }
            }
        }

        user.getClient().sendResponse(new RoomUsersGuildBadgesComposer(guildBadges));
    }

    /** The doorbell queue, polls, the word quiz, YouTube, the soundboard and raid protection. */
    private void sendRoomExtras(final User user, final Room room) {
        if (room.hasRights(user)
                || (room.hasGuild()
                        && room.getGuildRightLevel(user).isEqualOrGreaterThan(RoomRightLevels.GUILD_RIGHTS))) {
            if (!room.getUserQueue().isEmpty()) {
                for (User waiting : room.getUserQueue().values()) {
                    user.getClient()
                            .sendResponse(new DoorbellAddUserComposer(
                                    waiting.getUserInfo().getUsername()));
                }
            }
        }

        if (room.getPollId() > 0) {
            if (!PollManager.donePoll(user.getClient().getUser(), room.getPollId())) {
                Poll poll = Emulator.getGameEnvironment().getPollManager().getPoll(room.getPollId());

                if (poll != null) {
                    user.getClient().sendResponse(new PollStartComposer(poll));
                }
            }
        }

        if (room.hasActiveWordQuiz()) {
            user.getClient()
                    .sendResponse(new SimplePollStartComposer(
                            (Emulator.getIntUnixTimestamp() - room.wordQuizEnd) * 1000, room.wordQuiz));

            if (room.hasVotedInWordQuiz(user)) {
                user.getClient().sendResponse(new SimplePollAnswersComposer(room.noVotes, room.yesVotes));
            }
        }

        user.getClient()
                .sendResponse(
                        new polaris.messages.outgoing.rooms.youtube.YouTubeRoomSettingsComposer(room.isYoutubeEnabled())
                                .compose());

        if (!room.getYoutubeCurrentVideo().isEmpty()) {
            user.getClient()
                    .sendResponse(new polaris.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer(
                                    room.getYoutubeCurrentVideo(),
                                    room.getYoutubeSenderName(),
                                    room.getYoutubePlaylist())
                            .compose());
        }
        if (!room.getYoutubeWatchers().isEmpty()) {
            user.getClient()
                    .sendResponse(new polaris.messages.outgoing.rooms.youtube.YouTubeRoomWatchersComposer(
                                    room.getYoutubeWatchers())
                            .compose());
        }

        var soundboardManager = Emulator.getGameEnvironment().getSoundboardManager();
        user.getClient()
                .sendResponse(new polaris.messages.outgoing.soundboard.SoundboardSettingsComposer(
                                room.getSoundboardMode(),
                                soundboardManager.getCooldownSecondsForRank(
                                        user.getUserInfo().getRank().getId()),
                                soundboardManager.getSoundsForRank(
                                        user.getUserInfo().getRank().getId()))
                        .compose());

        // AIR 2671: whether the room info shows the raid protection button to this visitor.
        RaidProtectionManager.get().sendCapability(room, user);
    }

    /** Records the visit and returns true when it is the first one for this user. */
    static boolean markRoomVisited(UserStats stats, int roomId) {
        if (stats.visitedRoom(roomId)) return false;
        stats.addVisitRoom(roomId);
        return true;
    }

    /** Stamps the entry: seconds for the visit log and hosting time, room and millis for the entry throttle. */
    void markRoomOpened(User user, Room room) {
        user.getUserStats().roomEnterTimestamp = Emulator.getIntUnixTimestamp();
        user.getUserStats().roomOpenedId = room.getId();
        user.getUserStats().roomOpenedAtMillis = System.currentTimeMillis();
    }

    void logEnter(User user, Room room) {
        try {
            this.roomRepository.recordEntry(
                    room.getId(), user.getUserInfo().getId(), (int) user.getUserStats().roomEnterTimestamp);
            if (!user.getUserStats().visitedRoom(room.getId()))
                user.getUserStats().addVisitRoom(room.getId());
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    public void leaveRoom(User user, Room room) {
        this.leaveRoom(user, room, true);
    }

    /** Whole minutes between the entry and now, both unix seconds; 0 when the entry is unknown. */
    static int minutesInRoom(long enteredAtSeconds, long nowSeconds) {
        if (enteredAtSeconds <= 0 || nowSeconds <= enteredAtSeconds) {
            return 0;
        }
        return (int) ((nowSeconds - enteredAtSeconds) / 60);
    }

    public void leaveRoom(User user, Room room, boolean redirectToHotelView) {
        if (user.getUserInfo().getCurrentRoom() != null && user.getUserInfo().getCurrentRoom() == room) {
            user.getRoomUnit().setPathFinderRoom(null);

            this.logExit(user);
            // Before they are gone from the room: close the chests they asked to have closed.
            ChestAutoLock.onOwnerLeftRoom(room, user);
            room.removeUser(user, true);
            BuildersClubRoomSupport.sendCurrentRoomPlacementStatus(room);
            this.admitNextQueuedUser(room);

            if (redirectToHotelView) {
                user.getClient().sendResponse(new HotelViewComposer());
            }
            user.getUserInfo().setCurrentRoom(null);
            user.getRoomUnit().isKicked = false;

            if (room.getOwnerId() != user.getUserInfo().getId()) {
                AchievementManager.progressAchievement(
                        room.getOwnerId(),
                        Emulator.getGameEnvironment().getAchievementManager().getAchievement("RoomDecoHosting"),
                        minutesInRoom(user.getUserStats().roomEnterTimestamp, Emulator.getIntUnixTimestamp()));
            }

            user.getMessenger().connectionChanged(user, user.isOnline(), false);
        }
    }

    /**
     * A slot freed up: forward the user at the front of the room queue into
     * the room and refresh the position of everybody still waiting.
     */
    public void admitNextQueuedUser(Room room) {
        if (room == null || room.getUserCount() >= room.getUsersMax()) {
            return;
        }

        int[] nextQueued = RoomVisitorQueueSupport.pollNextQueued(room);
        while (nextQueued != null) {
            User next = Emulator.getGameEnvironment().getUserManager().getUser(nextQueued[0]);
            if (next != null && next.getClient() != null) {
                next.getUserInfo().setRoomQueueId(0);
                if (nextQueued[1] == RoomQueueStatusMessage.TARGET_SPECTATOR) {
                    // Leaving the spectator queue for a real slot: the official
                    // client drops spectator mode on YouAreNotSpectator (3242).
                    next.getClient().sendResponse(new YouAreNotSpectatorComposer(room.getId()));
                }
                next.getClient().sendResponse(new ForwardToRoomComposer(room.getId()));
                break;
            }
            nextQueued = RoomVisitorQueueSupport.pollNextQueued(room);
        }

        RoomVisitorQueueSupport.broadcastStatus(room);
    }

    public void logExit(User user) {
        Emulator.getPluginManager().fireEvent(new UserExitRoomEvent(user, UserExitRoomEvent.UserExitRoomReason.DOOR));
        if (user.getRoomUnit().getCacheable().containsKey("control")) {
            User control = (User) user.getRoomUnit().getCacheable().remove("control");
            control.getRoomUnit().getCacheable().remove("controller");
        }

        if (user.getUserInfo().getRiding() != null) {
            if (user.getUserInfo().getRiding().getRoomUnit() != null) {
                user.getUserInfo()
                        .getRiding()
                        .getRoomUnit()
                        .setGoalLocation(
                                user.getUserInfo().getRiding().getRoomUnit().getCurrentLocation());
            }
            user.getUserInfo().getRiding().setTask(PetTasks.FREE);
            user.getUserInfo().getRiding().setRider(null);
            user.getUserInfo().setRiding(null);
        }

        Room room = user.getUserInfo().getCurrentRoom();
        if (room != null) {
            try {
                this.roomRepository.recordExit(
                        room.getId(), user.getUserInfo().getId(), Emulator.getIntUnixTimestamp());
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    public Set<String> getTags() {
        return this.roomSearchService.tags();
    }

    public ArrayList<Room> getPublicRooms() {
        return this.roomSearchService.publicRooms();
    }

    public ArrayList<Room> getPopularRooms(int count) {
        return this.roomSearchService.popularRooms(count, RoomManager.SHOW_PUBLIC_IN_POPULAR_TAB);
    }

    public ArrayList<Room> getPopularRooms(int count, int category) {
        return this.roomSearchService.popularRooms(count, category);
    }

    public Map<Integer, List<Room>> getPopularRoomsByCategory(int count) {
        return this.roomSearchService.popularRoomsByCategory(count);
    }

    /**
     * The id of the room named exactly {@code name}, unless it is invisible; 0 when there is none. Only
     * the id is read: the name lookup used to run an unescaped LIKE and build and register up to 25 rooms
     * per request, and it answered with invisible rooms too.
     */
    public int findVisibleRoomIdByName(String name) {
        for (Room room : this.activeRooms.values()) {
            if (room.getState() != RoomState.INVISIBLE && room.getName().equalsIgnoreCase(name)) {
                return room.getId();
            }
        }

        try {
            return SqlQueries.queryOne(
                            "SELECT id FROM rooms WHERE name = ? AND state <> 'invisible' ORDER BY id LIMIT 1",
                            set -> set.getInt("id"),
                            name)
                    .orElse(0);
        } catch (SqlQueries.DataAccessException e) {
            LOGGER.error("Caught SQL exception", e);
            return 0;
        }
    }

    /** {@code text} for a LIKE pattern: % and _ match themselves instead of anything. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public ArrayList<Room> getRoomsWithName(String name) {
        ArrayList<Room> rooms = new ArrayList<>();

        for (Room room : this.activeRooms.values()) {
            if (room.getName().toLowerCase().contains(name.toLowerCase())) {
                rooms.add(room);
            }
        }

        if (rooms.size() < 25) {
            rooms.addAll(this.getOfflineRoomsWithName(name));
        }

        Collections.sort(rooms);

        return rooms;
    }

    private ArrayList<Room> getOfflineRoomsWithName(String name) {
        ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.username AS owner_name, rooms.* FROM rooms INNER JOIN users ON owner_id = users.id WHERE name LIKE ? ORDER BY id DESC LIMIT 25")) {
            statement.setString(1, "%" + escapeLike(name) + "%");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) continue;

                    Room r = new Room(set, this.roomDependencies());
                    rooms.add(r);
                    this.registerActiveRoom(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public ArrayList<Room> getRoomsWithTag(String tag) {
        return this.roomSearchService.roomsWithTag(tag);
    }

    public ArrayList<Room> getGroupRoomsWithName(String name) {
        ArrayList<Room> rooms = new ArrayList<>();

        for (Room room : this.activeRooms.values()) {
            if (room.getGuildId() == 0) continue;

            if (room.getName().toLowerCase().contains(name.toLowerCase())) rooms.add(room);
        }

        if (rooms.size() < 25) {
            rooms.addAll(this.getOfflineGroupRoomsWithName(name));
        }

        Collections.sort(rooms);

        return rooms;
    }

    private ArrayList<Room> getOfflineGroupRoomsWithName(String name) {
        ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT users.username AS owner_name, rooms.* FROM rooms INNER JOIN users ON rooms.owner_id = users.id WHERE name LIKE ? AND guild_id != 0 ORDER BY id DESC LIMIT 25")) {
            statement.setString(1, "%" + escapeLike(name) + "%");
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) continue;

                    Room r = new Room(set, this.roomDependencies());
                    rooms.add(r);

                    this.registerActiveRoom(r);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public ArrayList<Room> getRoomsFriendsNow(User user) {
        ArrayList<Room> rooms = new ArrayList<>();

        for (MessengerBuddy buddy : user.getMessenger().getFriends().values()) {
            if (!buddy.isVisibleOnline()) continue;

            User friend = Emulator.getGameEnvironment().getUserManager().getUser(buddy.getId());
            if (friend == null || friend.getUserInfo().getCurrentRoom() == null) continue;

            rooms.add(friend.getUserInfo().getCurrentRoom());
        }

        Collections.sort(rooms);

        return rooms;
    }

    public ArrayList<Room> getRoomsFriendsOwn(User user) {
        ArrayList<Room> rooms = new ArrayList<>();

        for (MessengerBuddy buddy : user.getMessenger().getFriends().values()) {
            if (!buddy.isVisibleOnline()) continue;

            User friend = Emulator.getGameEnvironment().getUserManager().getUser(buddy.getId());

            if (friend == null) continue;

            rooms.addAll(this.getRoomsForUser(friend));
        }

        Collections.sort(rooms);

        return rooms;
    }

    public ArrayList<Room> getRoomsVisited(User user, boolean includeSelf, int limit) {
        ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT rooms.* FROM room_enter_log INNER JOIN rooms ON room_enter_log.room_id = rooms.id WHERE user_id = ? AND timestamp >= ? AND rooms.owner_id != ? GROUP BY rooms.id ORDER BY MAX(timestamp) DESC LIMIT "
                                + limit)) {
            statement.setInt(1, user.getUserInfo().getId());
            statement.setInt(2, Emulator.getIntUnixTimestamp() - 259200);
            statement.setInt(3, (includeSelf ? 0 : user.getUserInfo().getId()));
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Room room = this.activeRooms.get(set.getInt("id"));

                    if (room == null) {
                        room = new Room(set, this.roomDependencies());

                        this.registerActiveRoom(room);
                    }

                    rooms.add(room);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        Collections.sort(rooms);

        return rooms;
    }

    public ArrayList<Room> getRoomsFavourite(User user) {
        final ArrayList<Room> rooms = new ArrayList<>();

        for (int value : user.getUserStats().getFavoriteRooms()) {
            Room room = RoomManager.this.getRoom(value);

            if (room != null) {
                if (room.getState() == RoomState.INVISIBLE) {
                    room.loadData();
                    if (!room.hasRights(user)) continue;
                }
                rooms.add(room);
            }
        }

        return rooms;
    }

    public List<Room> getGroupRooms(User user, int limit) {
        final ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT rooms.* FROM rooms INNER JOIN guilds_members ON guilds_members.guild_id = rooms.guild_id WHERE guilds_members.user_id = ? AND level_id != 3")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) {
                        rooms.add(this.activeRooms.get(set.getInt("id")));
                    } else {
                        rooms.add(new Room(set, this.roomDependencies()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        Collections.sort(rooms);

        return rooms.subList(0, (Math.min(rooms.size(), limit)));
    }

    public ArrayList<Room> getRoomsWithRights(User user) {
        ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT rooms.* FROM rooms INNER JOIN room_rights ON room_rights.room_id = rooms.id WHERE room_rights.user_id = ? ORDER BY rooms.id DESC LIMIT 30")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) {
                        rooms.add(this.activeRooms.get(set.getInt("id")));
                    } else {
                        rooms.add(new Room(set, this.roomDependencies()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public ArrayList<Room> getRoomsWithFriendsIn(User user, int limit) {
        final ArrayList<Room> rooms = new ArrayList<>();

        for (MessengerBuddy buddy : user.getMessenger().getFriends().values()) {
            User friend = Emulator.getGameEnvironment().getUserManager().getUser(buddy.getId());

            if (friend == null || friend.getUserInfo() == null) continue;

            Room room = friend.getUserInfo().getCurrentRoom();
            if (room != null && !rooms.contains(room) && room.hasRights(user)) rooms.add(room);

            if (rooms.size() >= limit) break;
        }

        Collections.sort(rooms);

        return rooms;
    }

    public List<Room> getTopRatedRooms(int limit) {
        final ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM rooms ORDER BY score DESC LIMIT ?")) {
            statement.setInt(1, limit);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) {
                        rooms.add(this.activeRooms.get(set.getInt("id")));
                    } else {
                        rooms.add(new Room(set, this.roomDependencies()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public ArrayList<Room> getRoomsWithAdminRights(User user) {
        ArrayList<Room> rooms = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM rooms INNER JOIN guilds_members ON guilds_members.guild_id = rooms.guild_id WHERE guilds_members.user_id = ? AND level_id = 0")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    if (this.activeRooms.containsKey(set.getInt("id"))) {
                        rooms.add(this.activeRooms.get(set.getInt("id")));
                    } else {
                        rooms.add(new Room(set, this.roomDependencies()));
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return rooms;
    }

    public ArrayList<Room> getRoomsInGroup(User user) {
        return new ArrayList<>();
    }

    public ArrayList<Room> getRoomsPromoted() {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : this.getActiveRooms()) {
            if (room.isPromoted()) {
                r.add(room);
            }
        }

        return r;
    }

    public ArrayList<Room> getRoomsStaffPromoted() {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : this.getActiveRooms()) {
            if (room.isStaffPromotedRoom()) {
                r.add(room);
            }
        }

        return r;
    }

    public List<Room> filterRoomsByOwner(List<Room> rooms, String filter) {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getOwnerName().equalsIgnoreCase(filter)) r.add(room);
        }

        return r;
    }

    public List<Room> filterRoomsByName(List<Room> rooms, String filter) {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getName().toLowerCase().contains(filter.toLowerCase())) r.add(room);
        }

        return r;
    }

    public List<Room> filterRoomsByNameAndDescription(List<Room> rooms, String filter) {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getName().toLowerCase().contains(filter.toLowerCase())
                    || room.getDescription().toLowerCase().contains(filter.toLowerCase())) r.add(room);
        }

        return r;
    }

    public List<Room> filterRoomsByTag(List<Room> rooms, String filter) {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getTags().split(";").length == 0) continue;

            for (String s : room.getTags().split(";")) {
                if (s.equalsIgnoreCase(filter)) r.add(room);
            }
        }

        return r;
    }

    public List<Room> filterRoomsByGroup(List<Room> rooms, String filter) {
        ArrayList<Room> r = new ArrayList<>();

        for (Room room : rooms) {
            if (room.getGuildId() == 0) continue;

            if (Emulator.getGameEnvironment()
                    .getGuildManager()
                    .getGuild(room.getGuildId())
                    .getName()
                    .toLowerCase()
                    .contains(filter.toLowerCase())) r.add(room);
        }

        return r;
    }

    public synchronized void dispose() {
        this.roomLifecycleService.dispose();
        LOGGER.info("Room Manager -> Disposed!");
    }

    public void quiesceRoomCycles() {
        this.roomLifecycleService.quiesceRoomCycles();
    }

    public CustomRoomLayout insertCustomLayout(Room room, String map, int doorX, int doorY, int doorDirection) {
        try {
            this.roomRepository.upsertCustomLayout(room.getId(), map, doorX, doorY, doorDirection);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return this.loadCustomLayout(room);
    }

    public void banUserFromRoom(User rights, int userId, int roomId, RoomBanTypes length) {
        this.roomModerationService.banUser(rights, userId, roomId, length);
    }

    public void registerGameType(Class<? extends Game> gameClass) {
        gameTypes.add(gameClass);
    }

    public void unregisterGameType(Class<? extends Game> gameClass) {
        gameTypes.remove(gameClass);
    }

    public ArrayList<Class<? extends Game>> getGameTypes() {
        return gameTypes;
    }

    public enum RoomBanTypes {
        RWUAM_BAN_USER_HOUR(60 * 60),
        RWUAM_BAN_USER_DAY(24 * 60 * 60),
        RWUAM_BAN_USER_PERM(10 * 365 * 24 * 60 * 60);

        public int duration;

        RoomBanTypes(int duration) {
            this.duration = duration;
        }
    }

    /**
     * Cached layout data from room_models to avoid repeated DB queries.
     * The raw data is shared; each Room gets its own RoomLayout instance.
     */
    static class RoomLayoutData {
        final String name;
        final int doorX;
        final int doorY;
        final int doorDir;
        final String heightmap;
        final boolean clubOnly;

        RoomLayoutData(ResultSet set) throws SQLException {
            this.name = set.getString("name");
            this.doorX = set.getInt("door_x");
            this.doorY = set.getInt("door_y");
            this.doorDir = set.getInt("door_dir");
            this.heightmap = set.getString("heightmap");
            this.clubOnly = clubOnly(set);
        }

        private static boolean clubOnly(ResultSet set) {
            try {
                return "1".equals(set.getString("club_only"));
            } catch (SQLException missingColumn) {
                return false;
            }
        }
    }
}
