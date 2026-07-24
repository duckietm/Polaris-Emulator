package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.rooms.HideDoorbellComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.rooms.RoomUnloadedEvent;
import com.eu.habbo.plugin.events.rooms.RoomUnloadingEvent;
import com.eu.habbo.threading.ThreadPooling;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.awt.Color;
import java.awt.Rectangle;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Room implements Comparable<Room>, ISerialize, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Room.class);
    private static final ThreadFactory LOAD_THREAD_FACTORY =
            Thread.ofVirtual().name("room-load-", 0).factory();
    private static final Executor LOAD_COORDINATOR =
            task -> LOAD_THREAD_FACTORY.newThread(task).start();

    // Manager instances for better separation of concerns
    private RoomTileManager tileManager;
    private RoomGameManager gameManager;
    private RoomTradeManager tradeManager;
    private RoomPromotionManager promotionManager;
    private RoomWordQuizManager wordQuizManager;
    private RoomRightsManager rightsManager;
    private RoomUnitManager unitManager;
    private RoomItemManager itemManager;
    private RoomChatManager chatManager;
    private RoomRollerManager rollerManager;
    private RoomMessagingManager messagingManager;
    private RoomCycleManager cycleManager;
    private RoomUserVariableManager userVariableManager;
    private RoomFurniVariableManager furniVariableManager;
    private RoomVariableManager roomVariableManager;

    public static final Comparator<Room> SORT_SCORE = (o1, o2) -> o2.getScore() - o1.getScore();
    public static final Comparator<Room> SORT_ID = (o1, o2) -> o2.getId() - o1.getId();
    private static final Int2ObjectMap<RoomMoodlightData> defaultMoodData = new Int2ObjectOpenHashMap<>();
    // Configuration. Loaded from database & updated accordingly.
    public static volatile boolean HABBO_CHAT_DELAY = false;
    public static volatile int MAXIMUM_BOTS = 10;
    public static volatile int MAXIMUM_PETS = 10;
    public static volatile int MAXIMUM_FURNI = 2500;
    public static volatile int MAXIMUM_POSTITNOTES = 200;
    public static volatile int HAND_ITEM_TIME = 10;
    public static volatile int IDLE_CYCLES = 240;
    public static volatile int IDLE_CYCLES_KICK = 480;
    public static volatile String PREFIX_FORMAT = "[<font color=\"%color%\">%prefix%</font>] ";
    public static volatile int ROLLERS_MAXIMUM_ROLL_AVATARS = 1;
    public static volatile boolean MUTEAREA_CAN_WHISPER = false;
    public static double MAXIMUM_FURNI_HEIGHT = 40d;
    public static final int WIRED_ACCESS_EVERYONE = 1;
    public static final int WIRED_ACCESS_USERS_WITH_RIGHTS = 2;
    public static final int WIRED_ACCESS_GROUP_MEMBERS = 4;
    public static final int WIRED_ACCESS_GROUP_ADMINS = 8;
    public static final int WIRED_ACCESS_ALLOWED_INSPECT_MASK = WIRED_ACCESS_EVERYONE
            | WIRED_ACCESS_USERS_WITH_RIGHTS
            | WIRED_ACCESS_GROUP_MEMBERS
            | WIRED_ACCESS_GROUP_ADMINS;
    public static final int WIRED_ACCESS_ALLOWED_MODIFY_MASK =
            WIRED_ACCESS_USERS_WITH_RIGHTS | WIRED_ACCESS_GROUP_MEMBERS | WIRED_ACCESS_GROUP_ADMINS;
    public static final int WIRED_ACCESS_DEFAULT_INSPECT_MASK = 0;
    public static final int WIRED_ACCESS_DEFAULT_MODIFY_MASK = 0;

    static {
        for (int i = 1; i <= 3; i++) {
            RoomMoodlightData data = RoomMoodlightData.fromString("");
            data.setId(i);
            defaultMoodData.put(i, data);
        }
    }

    public final Object roomUnitLock = new Object();
    public final List<Integer> userVotes;
    private final IntList rights;
    private final Int2IntMap mutedHabbos;
    private final Int2ObjectMap<RoomBan> bannedHabbos;
    private final Int2ObjectMap<RoomMoodlightData> moodlightData;
    private final RoomDependencies dependencies;
    private final RoomLifecycle lifecycle;
    private final RoomDisposer disposer = new RoomDisposer(this);
    private final RoomGuildService guildService = new RoomGuildService(this);
    private final RoomMediaSession media = new RoomMediaSession(this);
    private final RoomPostureService posture = new RoomPostureService(this);
    private final RoomLoader loader;
    private final RoomItemPersistence itemPersistence;
    private final RoomPersistence persistence;
    private final RoomRepository repository;
    private final RoomWiredAccessService wiredAccess;
    private final RoomWiredVisibilityService wiredVisibility = new RoomWiredVisibilityService(this);
    private final RoomWiredRuntime wiredRuntime = new RoomWiredRuntime(this);
    private final RoomUserCountPersistence userCountPersistence;
    public volatile double lastCycleCpuMs = 0.0;
    public volatile String lastCycleThread = "N/A";

    // Use appropriately. Could potentially cause memory leaks when used incorrectly.
    public volatile boolean preventUnloading = false;
    public volatile boolean preventUncaching = false;
    public Set<ServerMessage> scheduledComposers = ConcurrentHashMap.newKeySet();
    public final java.util.concurrent.ConcurrentLinkedQueue<Runnable> scheduledTasks =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    public String wordQuiz = "";
    public int noVotes = 0;
    public int yesVotes = 0;
    public int wordQuizEnd = 0;
    public volatile ScheduledFuture<?> roomCycleTask;
    private int id;
    private int ownerId;
    private volatile BiConsumer<Room, Integer> ownerChangeListener;
    private String ownerName;
    private String name;
    private String description;
    private volatile RoomLayout layout;
    private boolean overrideModel;
    private String layoutName;
    private String password;
    private RoomState state;
    private int usersMax;
    private int score;
    private int category;
    private String floorPaint;
    private String wallPaint;
    private String backgroundPaint;
    private int wallSize;
    private int wallHeight;
    private int floorSize;
    private int guild;
    private String tags;
    private boolean publicRoom;
    private boolean staffPromotedRoom;
    private boolean allowPets;
    private boolean allowPetsEat;
    private boolean allowWalkthrough;
    private boolean allowBotsWalk;
    private boolean allowEffects;
    private boolean hideWall;
    private int chatMode;
    private int chatWeight;
    private int chatSpeed;
    private int chatDistance;
    private int chatProtection;
    private int muteOption;
    private int kickOption;
    private int banOption;
    private int pollId;
    private int tradeMode;
    private boolean moveDiagonally;
    private boolean allowUnderpass;
    private boolean muteAllPets;
    private boolean leaveOnDoorTileEnabled;
    private boolean idleSleepEnabled;
    private int idleSleepTimeoutSeconds;
    private boolean idleAutokickEnabled;
    private int idleAutokickTimeoutSeconds;
    private boolean jukeboxActive;
    private boolean hideWired;
    private boolean buildersClubTrialLocked;
    private RoomState buildersClubOriginalState;
    private volatile boolean needsUpdate;
    private int rollerSpeed;
    private volatile Integer transientRollerSpeedOverride;
    private int lastTimerReset = Emulator.getIntUnixTimestamp();
    private volatile boolean muted;
    private volatile RoomSpecialTypes roomSpecialTypes;
    private TraxManager traxManager;

    public boolean isYoutubeEnabled() {
        return this.media.youtubeEnabled();
    }

    public void setYoutubeEnabled(boolean enabled) {
        this.media.youtubeEnabled(enabled);
    }

    public boolean isSoundboardEnabled() {
        return this.media.soundboardEnabled();
    }

    public void setSoundboardEnabled(boolean enabled) {
        this.media.soundboardEnabled(enabled);
    }

    public String getYoutubeCurrentVideo() {
        return this.media.currentVideo();
    }

    public String getYoutubeSenderName() {
        return this.media.senderName();
    }

    public java.util.List<String> getYoutubePlaylist() {
        return this.media.playlist();
    }

    public java.util.Set<Integer> getYoutubeWatchers() {
        return this.media.watchers();
    }

    public void setYoutubeVideo(String videoId, String senderName, java.util.List<String> playlist) {
        this.media.setVideo(videoId, senderName, playlist);
    }

    public void clearYoutubeVideo() {
        this.media.clearVideo();
    }

    public final Map<String, Object> cache;

    Room(int id, int ownerId) {
        this(id, ownerId, RoomDependencies.runtime());
    }

    Room(int id, int ownerId, RoomDependencies dependencies) {
        this.cache = new HashMap<>();
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.lifecycle = new RoomLifecycle(new RoomCycleTaskSlot());
        this.itemPersistence = new RoomItemPersistence(this.dependencies.database());
        this.persistence = new RoomPersistence(this.dependencies.database());
        this.repository = new RoomRepository(this.dependencies.database());
        this.wiredAccess = new RoomWiredAccessService(this, this.repository);
        this.id = id;
        this.ownerId = ownerId;
        this.userCountPersistence = this.createUserCountPersistence();
        this.bannedHabbos = new Int2ObjectOpenHashMap<>();
        this.moodlightData = new Int2ObjectOpenHashMap<>(defaultMoodData);
        this.mutedHabbos = new Int2IntOpenHashMap();
        this.rights = new IntArrayList();
        this.userVotes = new ArrayList<>();
        this.initializeManagers(new RoomChatManager(this, RoomChatManager.DEFAULT_MUTE_TIME_SECONDS, this.mutedHabbos));
        this.loader = this.createLoader();
    }

    public Room(ResultSet set) throws SQLException {
        this(set, RoomDependencies.runtime());
    }

    Room(ResultSet set, RoomDependencies dependencies) throws SQLException {
        this.cache = new HashMap<>(1000);
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies");
        this.lifecycle = new RoomLifecycle(new RoomCycleTaskSlot());
        this.itemPersistence = new RoomItemPersistence(this.dependencies.database());
        this.persistence = new RoomPersistence(this.dependencies.database());
        this.repository = new RoomRepository(this.dependencies.database());
        this.wiredAccess = new RoomWiredAccessService(this, this.repository);
        RoomSnapshot.Initial initial = RoomSnapshot.readInitial(set);
        this.id = initial.id();
        this.ownerId = initial.ownerId();
        this.userCountPersistence = this.createUserCountPersistence();
        this.ownerName = initial.ownerName();
        this.name = initial.name();
        this.description = initial.description();
        this.password = initial.password();
        this.state = initial.state();
        this.usersMax = initial.usersMax();
        this.score = initial.score();
        this.category = initial.category();
        this.floorPaint = initial.floorPaint();
        this.wallPaint = initial.wallPaint();
        this.backgroundPaint = initial.backgroundPaint();
        this.wallSize = initial.wallSize();
        this.wallHeight = initial.wallHeight();
        this.floorSize = initial.floorSize();
        this.tags = initial.tags();
        this.publicRoom = initial.publicRoom();
        this.staffPromotedRoom = initial.staffPromotedRoom();
        this.allowPets = initial.allowPets();
        this.allowPetsEat = initial.allowPetsEat();
        this.allowWalkthrough = initial.allowWalkthrough();
        this.hideWall = initial.hideWall();
        this.setYoutubeEnabled(initial.youtubeEnabled());
        this.setSoundboardEnabled(initial.soundboardEnabled());
        this.chatMode = initial.chatMode();
        this.chatWeight = initial.chatWeight();
        this.chatSpeed = initial.chatSpeed();
        this.chatDistance = initial.chatDistance();
        this.chatProtection = initial.chatProtection();
        this.muteOption = initial.muteOption();
        this.kickOption = initial.kickOption();
        this.banOption = initial.banOption();
        this.pollId = initial.pollId();
        this.guild = initial.guild();
        this.rollerSpeed = initial.rollerSpeed();
        this.overrideModel = initial.overrideModel();
        this.layoutName = initial.layoutName();
        this.jukeboxActive = initial.jukeboxActive();
        this.hideWired = initial.hideWired();
        this.buildersClubTrialLocked = initial.buildersClubTrialLocked();
        this.buildersClubOriginalState = initial.buildersClubOriginalState();

        this.bannedHabbos = new Int2ObjectOpenHashMap<>();

        try (Connection connection = this.dependencies.database().openConnection()) {
            // Load bans eagerly (needed for entry check before loadData)
            RoomBanLoader.load(connection, this, this.bannedHabbos);
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        RoomSnapshot snapshot = RoomSnapshot.complete(initial, set);
        this.tradeMode = snapshot.postBanLoad().tradeMode();
        this.moveDiagonally = snapshot.postBanLoad().moveDiagonally();
        this.allowUnderpass = snapshot.postBanLoad().allowUnderpass();
        this.muteAllPets = snapshot.postBanLoad().muteAllPets();
        this.leaveOnDoorTileEnabled = snapshot.postBanLoad().leaveOnDoorTileEnabled();
        this.idleSleepEnabled = snapshot.postBanLoad().idleSleepEnabled();
        this.idleSleepTimeoutSeconds = snapshot.postBanLoad().idleSleepTimeoutSeconds();
        this.idleAutokickEnabled = snapshot.postBanLoad().idleAutokickEnabled();
        this.idleAutokickTimeoutSeconds = snapshot.postBanLoad().idleAutokickTimeoutSeconds();

        this.allowBotsWalk = true;
        this.allowEffects = true;
        this.moodlightData = new Int2ObjectOpenHashMap<>(defaultMoodData);

        for (String s : snapshot.postBanLoad().moodlightData().split(";")) {
            RoomMoodlightData data = RoomMoodlightData.fromString(s);
            this.moodlightData.put(data.getId(), data);
        }

        this.mutedHabbos = new Int2IntOpenHashMap();

        this.rights = new IntArrayList();
        this.userVotes = new ArrayList<>();

        // Initialize managers
        this.initializeManagers();
        this.promotionManager.setPromoted(initial.promoted());
        this.loader = this.createLoader();
    }

    /**
     * Initializes all manager instances for this room.
     */
    private void initializeManagers() {
        this.initializeManagers(new RoomChatManager(this, this.mutedHabbos));
    }

    private void initializeManagers(RoomChatManager chatManager) {
        this.tileManager = new RoomTileManager(this);
        this.gameManager = new RoomGameManager(this);
        this.tradeManager = new RoomTradeManager(this);
        this.promotionManager = new RoomPromotionManager(this);
        this.wordQuizManager = new RoomWordQuizManager(this);
        this.rightsManager = new RoomRightsManager(this, this.rights, this.bannedHabbos, this.mutedHabbos);
        this.unitManager = new RoomUnitManager(this);
        this.itemManager = new RoomItemManager(this);
        this.chatManager = chatManager;
        this.rollerManager = new RoomRollerManager(this);
        this.messagingManager = new RoomMessagingManager(this);
        this.cycleManager = new RoomCycleManager(this);
        this.userVariableManager = new RoomUserVariableManager(this);
        this.furniVariableManager = new RoomFurniVariableManager(this);
        this.roomVariableManager = new RoomVariableManager(this);
    }

    // ==================== MANAGER GETTERS ====================

    /**
     * Gets the tile manager for this room.
     */
    public RoomTileManager getTileManager() {
        return this.tileManager;
    }

    /**
     * Gets the game manager for this room.
     */
    public RoomGameManager getGameManager() {
        return this.gameManager;
    }

    /**
     * Gets the trade manager for this room.
     */
    public RoomTradeManager getTradeManager() {
        return this.tradeManager;
    }

    /**
     * Gets the promotion manager for this room.
     */
    public RoomPromotionManager getPromotionManager() {
        return this.promotionManager;
    }

    /**
     * Gets the word quiz manager for this room.
     */
    public RoomWordQuizManager getWordQuizManager() {
        return this.wordQuizManager;
    }

    /**
     * Gets the rights manager for this room.
     */
    public RoomRightsManager getRightsManager() {
        return this.rightsManager;
    }

    /**
     * Gets the unit manager for this room.
     */
    public RoomUnitManager getUnitManager() {
        return this.unitManager;
    }

    /**
     * Gets the item manager for this room.
     */
    public RoomItemManager getItemManager() {
        return this.itemManager;
    }

    /**
     * Gets the chat manager for this room.
     */
    public RoomChatManager getChatManager() {
        return this.chatManager;
    }

    /**
     * Gets the messaging manager for this room.
     */
    public RoomMessagingManager getMessagingManager() {
        return this.messagingManager;
    }

    /**
     * Gets the cycle manager for this room.
     */
    public RoomCycleManager getCycleManager() {
        return this.cycleManager;
    }

    public RoomUserVariableManager getUserVariableManager() {
        return this.userVariableManager;
    }

    public RoomFurniVariableManager getFurniVariableManager() {
        return this.furniVariableManager;
    }

    public RoomVariableManager getRoomVariableManager() {
        return this.roomVariableManager;
    }

    /**
     * Gets the roller manager for this room.
     */
    public RoomRollerManager getRollerManager() {
        return this.rollerManager;
    }

    /**
     * Checks if the room is currently loading data.
     */
    public boolean isLoadingInProgress() {
        return this.lifecycle.isLoading();
    }

    /**
     * Checks if the room data is loaded or is currently being loaded.
     */
    public boolean isLoadedOrLoading() {
        return this.lifecycle.isLoadedOrLoading();
    }

    long beginLoadTransition() {
        return this.lifecycle.beginLoad();
    }

    boolean publishLoadTransition(long generation, Supplier<ScheduledFuture<?>> cycleScheduler) {
        return this.lifecycle.publishLoad(generation, cycleScheduler);
    }

    boolean beginUnloadTransition() {
        return this.lifecycle.beginUnload();
    }

    void finishUnloadTransition() {
        this.lifecycle.finishUnload();
    }

    void quiesceCycleTask() {
        synchronized (this) {
            this.lifecycle.quiesceCycle();
        }
    }

    void resetIdleCycles() {
        this.lifecycle.resetIdleCycles();
    }

    boolean advanceIdleUnload(boolean empty) {
        return this.lifecycle.advanceIdleUnload(empty);
    }

    boolean prepareLoadTransition(long generation) {
        return this.lifecycle.prepareLoad(generation);
    }

    void failLoadTransition(long generation) {
        this.lifecycle.failLoad(generation);
    }

    /**
     * Starts loading room data asynchronously in the background.
     * This allows the room to start loading before the user fully enters,
     * reducing perceived load time.
     */
    public void startBackgroundLoad() {
        this.lifecycle.startBackgroundLoad(LOAD_COORDINATOR, this::loadDataInternal);
    }

    /**
     * Waits for background loading to complete if it's in progress.
     * If loading hasn't started yet, starts loading synchronously.
     */
    public void waitForLoad() {
        if (this.lifecycle.isLoaded()) {
            return;
        }
        CompletableFuture<Void> future = this.lifecycle.loadingFuture();

        if (future != null) {
            try {
                future.join();
            } catch (Exception e) {
                LOGGER.error("Error waiting for room load", e);
            }
        } else {
            this.loadData();
        }
    }

    public void loadData() {
        RoomLifecycle.LoadAttempt attempt = this.lifecycle.beginOrJoinLoad();
        CompletableFuture<Void> futureToWait = attempt.future();

        // Wait for existing load outside the lock
        if (futureToWait != null) {
            try {
                futureToWait.join();
            } catch (Exception e) {
                LOGGER.error("Error waiting for room load", e);
            }
            return;
        }

        // Load if needed
        if (attempt.generation() >= 0L) {
            this.loadDataInternal(attempt.generation());
        }
    }

    /**
     * Internal method that performs the actual room data loading.
     * Uses parallel loading for independent operations to reduce total load time.
     */
    private void loadDataInternal(long generation) {
        try {
            this.performLoadData(generation);
        } catch (Exception exception) {
            LOGGER.error("Caught exception during room load", exception);
        } finally {
            this.failLoadTransition(generation);
        }
    }

    private void performLoadData(long generation) {
        this.loader.load(generation);
    }

    private RoomLoader createLoader() {
        return new RoomLoader(
                new RoomLoadOperations(this, this.dependencies.database()),
                () -> Emulator.getThreading().getService());
    }

    GameEnvironment gameEnvironment() {
        return Emulator.getGameEnvironment();
    }

    PluginManager pluginManager() {
        return Emulator.getPluginManager();
    }

    ThreadPooling threading() {
        return Emulator.getThreading();
    }

    int currentUnixTimestamp() {
        return Emulator.getIntUnixTimestamp();
    }

    private final class RoomCycleTaskSlot implements RoomLifecycle.CycleTaskSlot {

        @Override
        public ScheduledFuture<?> get() {
            return Room.this.roomCycleTask;
        }

        @Override
        public void set(ScheduledFuture<?> task) {
            Room.this.roomCycleTask = task;
        }
    }

    public void updateTile(RoomTile tile) {
        this.tileManager.updateTile(tile);
    }

    public void updateTiles(Collection<RoomTile> tiles) {
        this.tileManager.updateTiles(tiles);
    }

    public RoomTileState calculateTileState(RoomTile tile) {
        return this.tileManager.calculateTileState(tile);
    }

    public RoomTileState calculateTileState(RoomTile tile, HabboItem exclude) {
        return this.tileManager.calculateTileState(tile, exclude);
    }

    public boolean tileWalkable(RoomTile t) {
        return this.tileManager.tileWalkable(t);
    }

    public boolean tileWalkable(short x, short y) {
        return this.tileManager.tileWalkable(x, y);
    }

    public void pickUpItem(HabboItem item, Habbo picker) {
        this.itemManager.pickUpItem(item, picker);
    }

    public void updateHabbosAt(Rectangle rectangle) {
        for (short i = (short) rectangle.x; i < rectangle.x + rectangle.width; i++) {
            for (short j = (short) rectangle.y; j < rectangle.y + rectangle.height; j++) {
                this.updateHabbosAt(i, j);
            }
        }
    }

    public void updateHabbo(Habbo habbo) {
        this.updateRoomUnit(habbo.getRoomUnit());
    }

    public void updateRoomUnit(RoomUnit roomUnit) {
        this.posture.update(roomUnit);
    }

    public void updateHabbosAt(short x, short y) {
        this.unitManager.updateHabbosAt(x, y);
    }

    public void updateHabbosAt(short x, short y, Collection<Habbo> habbos) {
        this.unitManager.updateHabbosAt(x, y, habbos);
    }

    public void updateBotsAt(short x, short y) {
        this.unitManager.updateBotsAt(x, y);
    }

    public void updatePetsAt(short x, short y) {
        this.unitManager.updatePetsAt(x, y);
    }

    public void pickupPetsForHabbo(Habbo habbo) {
        this.unitManager.pickupPetsForHabbo(habbo);
    }

    public void startTrade(Habbo userOne, Habbo userTwo) {
        this.tradeManager.startTrade(userOne, userTwo);
    }

    public void stopTrade(RoomTrade trade) {
        this.tradeManager.stopTrade(trade);
    }

    public RoomTrade getActiveTradeForHabbo(Habbo user) {
        return this.tradeManager.getActiveTradeForHabbo(user);
    }

    public synchronized void dispose() {
        this.lifecycle.dispose(
                this.preventUnloading,
                () -> Emulator.getPluginManager()
                        .fireEvent(new RoomUnloadingEvent(this))
                        .isCancelled(),
                this.disposer::dispose,
                () -> Emulator.getPluginManager().fireEvent(new RoomUnloadedEvent(this)));
    }

    @Override
    public int compareTo(Room o) {
        if (o.getUserCount() != this.getUserCount()) {
            return o.getCurrentHabbos().size() - this.getCurrentHabbos().size();
        }

        return this.id - o.id;
    }

    @Override
    public void serialize(ServerMessage message) {
        RoomSerializer.serialize(this, message);
    }

    @Override
    public void run() {
        synchronized (this) {
            boolean runCycle = this.lifecycle.isLoaded();

            if (runCycle) {
                try {
                    long startTime = System.nanoTime();
                    this.lastCycleThread = Thread.currentThread().getName();
                    // Run cycle directly instead of scheduling on thread pool
                    // This ensures all cycle tasks in the same tick execute synchronously
                    // preventing wired desync issues
                    this.cycle();
                    this.lastCycleCpuMs = (System.nanoTime() - startTime) / 1000000.0;
                } catch (Exception e) {
                    LOGGER.error("Caught exception", e);
                }
            }

            this.save();
        }
    }

    public void save() {
        if (this.needsUpdate) {
            try {
                this.persistence.save(RoomPersistentStateFactory.capture(this));
                this.needsUpdate = false;
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }

    void savePendingItems(List<HabboItem> items) {
        try {
            this.itemPersistence.save(items);
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception saving room items", exception);
        }
    }

    /**
     * Updates the user count in the database.
     * Made public for access by RoomUnitManager.
     */
    public void updateDatabaseUserCount() {
        try {
            this.repository.updateUserCount(this.id, this.getUserCount());
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    void scheduleDatabaseUserCountUpdate() {
        this.userCountPersistence.schedule();
    }

    private RoomUserCountPersistence createUserCountPersistence() {
        return new RoomUserCountPersistence(
                this::getUserCount,
                userCount -> this.repository.updateUserCount(this.id, userCount),
                this.dependencies.persistence()::execute);
    }

    private void cycle() {
        this.cycleManager.cycle();
    }

    public int getId() {
        return this.id;
    }

    public int getOwnerId() {
        return this.ownerId;
    }

    public void setOwnerId(int ownerId) {
        int previousOwnerId = this.ownerId;
        this.ownerId = ownerId;

        BiConsumer<Room, Integer> listener = this.ownerChangeListener;
        if (listener != null && previousOwnerId != ownerId) {
            listener.accept(this, previousOwnerId);
        }
    }

    void setOwnerChangeListener(BiConsumer<Room, Integer> ownerChangeListener) {
        this.ownerChangeListener = ownerChangeListener;
    }

    public String getOwnerName() {
        return this.ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;

        if (this.name.length() > 50) {
            this.name = this.name.substring(0, 50);
        }

        if (this.hasGuild()) {
            Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);

            if (guild != null) {
                guild.setRoomName(name);
            }
        }
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;

        if (this.description.length() > 250) {
            this.description = this.description.substring(0, 250);
        }
    }

    public RoomLayout getLayout() {
        return this.layout;
    }

    RoomLayout currentLayout() {
        return this.layout;
    }

    String layoutName() {
        return this.layoutName;
    }

    public void setLayout(RoomLayout layout) {
        this.layout = layout;
    }

    public boolean hasCustomLayout() {
        return this.overrideModel;
    }

    public void setHasCustomLayout(boolean overrideModel) {
        this.overrideModel = overrideModel;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;

        if (this.password.length() > 20) {
            this.password = this.password.substring(0, 20);
        }
    }

    public RoomState getState() {
        return this.state;
    }

    public void setState(RoomState state) {
        this.state = state;
    }

    public boolean isBuildersClubTrialLocked() {
        return this.buildersClubTrialLocked;
    }

    public void setBuildersClubTrialLocked(boolean buildersClubTrialLocked) {
        this.buildersClubTrialLocked = buildersClubTrialLocked;
    }

    public RoomState getBuildersClubOriginalState() {
        return this.buildersClubOriginalState;
    }

    public void setBuildersClubOriginalState(RoomState buildersClubOriginalState) {
        this.buildersClubOriginalState = buildersClubOriginalState;
    }

    public int getUsersMax() {
        return this.usersMax;
    }

    public void setUsersMax(int usersMax) {
        this.usersMax = usersMax;
    }

    public int getScore() {
        return this.score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getCategory() {
        return this.category;
    }

    public void setCategory(int category) {
        this.category = category;
    }

    public String getFloorPaint() {
        return this.floorPaint;
    }

    public void setFloorPaint(String floorPaint) {
        this.floorPaint = floorPaint;
    }

    public String getWallPaint() {
        return this.wallPaint;
    }

    public void setWallPaint(String wallPaint) {
        this.wallPaint = wallPaint;
    }

    public String getBackgroundPaint() {
        return this.backgroundPaint;
    }

    public void setBackgroundPaint(String backgroundPaint) {
        this.backgroundPaint = backgroundPaint;
    }

    public int getWallSize() {
        return this.wallSize;
    }

    public void setWallSize(int wallSize) {
        this.wallSize = wallSize;
    }

    public int getWallHeight() {
        return this.wallHeight;
    }

    public void setWallHeight(int wallHeight) {
        this.wallHeight = wallHeight;
    }

    public int getFloorSize() {
        return this.floorSize;
    }

    public void setFloorSize(int floorSize) {
        this.floorSize = floorSize;
    }

    public String getTags() {
        return this.tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public int getTradeMode() {
        return this.tradeMode;
    }

    public void setTradeMode(int tradeMode) {
        this.tradeMode = tradeMode;
    }

    public boolean moveDiagonally() {
        return this.moveDiagonally;
    }

    public void moveDiagonally(boolean moveDiagonally) {
        this.moveDiagonally = moveDiagonally;
        this.layout.moveDiagonally(this.moveDiagonally);
        this.needsUpdate = true;
    }

    public int getGuildId() {
        return this.guild;
    }

    public boolean hasGuild() {
        return this.getGuildId() != 0;
    }

    public boolean belongsToGuild() {
        return this.guild > 0;
    }

    public void setGuild(int guild) {
        this.guild = guild;
    }

    public String getGuildName() {
        return this.guildService.name();
    }

    public boolean isPublicRoom() {
        return this.publicRoom;
    }

    public void setPublicRoom(boolean publicRoom) {
        this.publicRoom = publicRoom;
    }

    public boolean isStaffPromotedRoom() {
        return this.staffPromotedRoom;
    }

    public void setStaffPromotedRoom(boolean staffPromotedRoom) {
        this.staffPromotedRoom = staffPromotedRoom;
    }

    public boolean isAllowPets() {
        return this.allowPets;
    }

    public void setAllowPets(boolean allowPets) {
        this.allowPets = allowPets;
        if (!allowPets) {
            removeAllPets(ownerId);
        }
    }

    public boolean isAllowPetsEat() {
        return this.allowPetsEat;
    }

    public void setAllowPetsEat(boolean allowPetsEat) {
        this.allowPetsEat = allowPetsEat;
    }

    public boolean isAllowWalkthrough() {
        return this.allowWalkthrough;
    }

    public void setAllowWalkthrough(boolean allowWalkthrough) {
        this.allowWalkthrough = allowWalkthrough;
    }

    public boolean isAllowUnderpass() {
        return this.allowUnderpass;
    }

    public void setAllowUnderpass(boolean allowUnderpass) {
        this.allowUnderpass = allowUnderpass;
    }

    public boolean isMuteAllPets() {
        return this.muteAllPets;
    }

    public void setMuteAllPets(boolean muteAllPets) {
        this.muteAllPets = muteAllPets;
    }

    public boolean isLeaveOnDoorTileEnabled() {
        return this.leaveOnDoorTileEnabled;
    }

    public void setLeaveOnDoorTileEnabled(boolean leaveOnDoorTileEnabled) {
        this.leaveOnDoorTileEnabled = leaveOnDoorTileEnabled;
    }

    public boolean isIdleSleepEnabled() {
        return this.idleSleepEnabled;
    }

    public void setIdleSleepEnabled(boolean idleSleepEnabled) {
        this.idleSleepEnabled = idleSleepEnabled;
    }

    public int getIdleSleepTimeoutSeconds() {
        return this.idleSleepTimeoutSeconds;
    }

    public void setIdleSleepTimeoutSeconds(int idleSleepTimeoutSeconds) {
        this.idleSleepTimeoutSeconds = idleSleepTimeoutSeconds;
    }

    public boolean isIdleAutokickEnabled() {
        return this.idleAutokickEnabled;
    }

    public void setIdleAutokickEnabled(boolean idleAutokickEnabled) {
        this.idleAutokickEnabled = idleAutokickEnabled;
    }

    public int getIdleAutokickTimeoutSeconds() {
        return this.idleAutokickTimeoutSeconds;
    }

    public void setIdleAutokickTimeoutSeconds(int idleAutokickTimeoutSeconds) {
        this.idleAutokickTimeoutSeconds = idleAutokickTimeoutSeconds;
    }

    public boolean isAllowBotsWalk() {
        return this.allowBotsWalk;
    }

    public void setAllowBotsWalk(boolean allowBotsWalk) {
        this.allowBotsWalk = allowBotsWalk;
    }

    public boolean isAllowEffects() {
        return this.allowEffects;
    }

    public void setAllowEffects(boolean allowEffects) {
        this.allowEffects = allowEffects;
    }

    public boolean isHideWall() {
        return this.hideWall;
    }

    public void setHideWall(boolean hideWall) {
        this.hideWall = hideWall;
    }

    public Color getBackgroundTonerColor() {
        return RoomVisualSettings.backgroundTonerColor(this.itemManager);
    }

    public int getChatMode() {
        return this.chatMode;
    }

    public void setChatMode(int chatMode) {
        this.chatMode = chatMode;
    }

    public int getChatWeight() {
        return this.chatWeight;
    }

    public void setChatWeight(int chatWeight) {
        this.chatWeight = chatWeight;
    }

    public int getChatSpeed() {
        return this.chatSpeed;
    }

    public void setChatSpeed(int chatSpeed) {
        this.chatSpeed = chatSpeed;
    }

    public int getChatDistance() {
        return this.chatDistance;
    }

    public void setChatDistance(int chatDistance) {
        this.chatDistance = chatDistance;
    }

    public void removeAllPets() {
        this.unitManager.removeAllPets();
    }

    /**
     * Removes all pets from the room except if the owner id is excludeUserId
     *
     * @param excludeUserId Habbo id to keep pets
     */
    public void removeAllPets(int excludeUserId) {
        this.unitManager.removeAllPets(excludeUserId);
    }

    public int getChatProtection() {
        return this.chatProtection;
    }

    public void setChatProtection(int chatProtection) {
        this.chatProtection = chatProtection;
    }

    public int getMuteOption() {
        return this.muteOption;
    }

    public void setMuteOption(int muteOption) {
        this.muteOption = muteOption;
    }

    public int getKickOption() {
        return this.kickOption;
    }

    public void setKickOption(int kickOption) {
        this.kickOption = kickOption;
    }

    public int getBanOption() {
        return this.banOption;
    }

    public void setBanOption(int banOption) {
        this.banOption = banOption;
    }

    public int getPollId() {
        return this.pollId;
    }

    public void setPollId(int pollId) {
        this.pollId = pollId;
    }

    public int getRollerSpeed() {
        return this.rollerSpeed;
    }

    public void setRollerSpeed(int rollerSpeed) {
        this.rollerSpeed = rollerSpeed;
        this.needsUpdate = true;
    }

    public Integer getTransientRollerSpeedOverride() {
        return this.transientRollerSpeedOverride;
    }

    public void setTransientRollerSpeedOverride(Integer rollerSpeed) {
        this.transientRollerSpeedOverride = rollerSpeed;
    }

    public String[] filterAnything() {
        return new String[] {this.getOwnerName(), this.getGuildName(), this.getDescription(), this.getPromotionDesc()};
    }

    public long getCycleTimestamp() {
        return this.cycleManager.getCycleTimestamp();
    }

    public boolean isPromoted() {
        return this.promotionManager.isPromoted();
    }

    public RoomPromotion getPromotion() {
        return this.promotionManager.getPromotion();
    }

    public String getPromotionDesc() {
        return this.promotionManager.getPromotionDesc();
    }

    public void createPromotion(String title, String description, int category) {
        this.promotionManager.createPromotion(title, description, category);
    }

    public boolean addGame(Game game) {
        return this.gameManager.addGame(game);
    }

    public boolean deleteGame(Game game) {
        return this.gameManager.deleteGame(game);
    }

    public Game getGame(Class<? extends Game> gameType) {
        return this.gameManager.getGame(gameType);
    }

    public Game getGameOrCreate(Class<? extends Game> gameType) {
        return this.gameManager.getGameOrCreate(gameType);
    }

    public Set<Game> getGames() {
        return this.gameManager.getGames();
    }

    public int getUserCount() {
        return this.unitManager.getHabboCount();
    }

    public ConcurrentHashMap<Integer, Habbo> getCurrentHabbos() {
        return this.unitManager.getCurrentHabbos();
    }

    public Collection<Habbo> getHabbos() {
        return this.unitManager.getHabbos();
    }

    public Int2ObjectMap<Habbo> getHabboQueue() {
        return this.unitManager.getHabboQueue();
    }

    public Int2ObjectMap<String> getFurniOwnerNames() {
        return this.itemManager.getFurniOwnerNames();
    }

    public String getFurniOwnerName(int userId) {
        return this.itemManager.getFurniOwnerName(userId);
    }

    public Int2IntMap getFurniOwnerCount() {
        return this.itemManager.getFurniOwnerCount();
    }

    public Int2ObjectMap<RoomMoodlightData> getMoodlightData() {
        return this.moodlightData;
    }

    public int getLastTimerReset() {
        return this.lastTimerReset;
    }

    public void setLastTimerReset(int lastTimerReset) {
        this.lastTimerReset = lastTimerReset;
    }

    public void addToQueue(Habbo habbo) {
        this.unitManager.addToQueue(habbo);
    }

    public boolean removeFromQueue(Habbo habbo) {
        try {
            this.sendComposer(new HideDoorbellComposer(habbo.getHabboInfo().getUsername()).compose());

            return this.unitManager.removeFromQueue(habbo.getHabboInfo().getId()) != null;
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        return true;
    }

    public Int2ObjectMap<Bot> getCurrentBots() {
        return this.unitManager.getCurrentBots();
    }

    public Int2ObjectMap<Pet> getCurrentPets() {
        return this.unitManager.getCurrentPets();
    }

    public Set<String> getWordFilterWords() {
        return this.chatManager.getWordFilterWords();
    }

    public RoomSpecialTypes getRoomSpecialTypes() {
        return this.roomSpecialTypes;
    }

    void replaceSpecialTypes(RoomSpecialTypes roomSpecialTypes) {
        this.roomSpecialTypes = roomSpecialTypes;
    }

    /**
     * Alias for getRoomSpecialTypes() for shorter access.
     */
    public RoomSpecialTypes getSpecialTypes() {
        return this.roomSpecialTypes;
    }

    public boolean isPreLoaded() {
        return this.lifecycle.isPreloaded();
    }

    public boolean isLoaded() {
        return this.lifecycle.isLoaded();
    }

    public long getLifecycleGeneration() {
        return this.lifecycle.generation();
    }

    public long getWiredCacheGeneration() {
        return this.wiredRuntime.cacheGeneration();
    }

    public long advanceWiredCacheGeneration() {
        return this.wiredRuntime.advanceCacheGeneration();
    }

    public RoomWiredRuntime getWiredRuntime() {
        return this.wiredRuntime;
    }

    void onFurnitureTopologyChanged() {
        this.wiredRuntime.onFurnitureTopologyChanged();
    }

    void forgetWiredGravity(HabboItem item) {
        this.wiredRuntime.forgetGravity(item);
    }

    void disposeWiredRuntimeState() {
        this.wiredRuntime.dispose();
    }

    public void setNeedsUpdate(boolean needsUpdate) {
        this.needsUpdate = needsUpdate;
    }

    public IntList getRights() {
        return this.rights;
    }

    public boolean isMuted() {
        return this.muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public TraxManager getTraxManager() {
        return this.traxManager;
    }

    void replaceTraxManager(TraxManager traxManager) {
        this.traxManager = traxManager;
    }

    public void addHabboItem(HabboItem item) {
        this.itemManager.addHabboItem(item);
    }

    public HabboItem getHabboItem(int id) {
        return this.itemManager.getHabboItem(id);
    }

    public long getItemIncarnation(int id) {
        return this.itemManager.getItemIncarnation(id);
    }

    void removeHabboItem(int id) {
        this.itemManager.removeHabboItem(id);
    }

    public void removeHabboItem(HabboItem item) {
        this.itemManager.removeHabboItem(item);
    }

    public Set<HabboItem> getFloorItems() {
        return this.itemManager.getFloorItems();
    }

    public Set<HabboItem> getWallItems() {
        return this.itemManager.getWallItems();
    }

    public Set<HabboItem> getPostItNotes() {
        return this.itemManager.getPostItNotes();
    }

    public void addHabbo(Habbo habbo) {
        this.unitManager.addHabbo(habbo);
    }

    public void kickHabbo(Habbo habbo, boolean alert) {
        this.unitManager.kickHabbo(habbo, alert);
    }

    public void removeHabbo(Habbo habbo) {
        this.cleanupYoutubeWatcher(habbo);
        this.unitManager.removeHabbo(habbo);
    }

    public void removeHabbo(Habbo habbo, boolean sendRemovePacket) {
        this.cleanupYoutubeWatcher(habbo);
        this.unitManager.removeHabbo(habbo, sendRemovePacket);
    }

    private void cleanupYoutubeWatcher(Habbo habbo) {
        this.media.removeWatcher(habbo);
    }

    public void addBot(Bot bot) {
        this.unitManager.addBot(bot);
    }

    public void addPet(Pet pet) {
        this.unitManager.addPet(pet);
    }

    public Bot getBot(int botId) {
        return this.unitManager.getBot(botId);
    }

    public Bot getBot(RoomUnit roomUnit) {
        return this.unitManager.getBot(roomUnit);
    }

    public Bot getBotByRoomUnitId(int id) {
        return this.unitManager.getBotByRoomUnitId(id);
    }

    public List<Bot> getBots(String name) {
        return this.unitManager.getBots(name);
    }

    public boolean hasBotsAt(final int x, final int y) {
        return this.unitManager.hasBotsAt(x, y);
    }

    public Pet getPet(int petId) {
        return this.unitManager.getPet(petId);
    }

    public Pet getPet(RoomUnit roomUnit) {
        return this.unitManager.getPet(roomUnit);
    }

    public boolean removeBot(Bot bot) {
        return this.unitManager.removeBot(bot);
    }

    public void placePet(Pet pet, short x, short y, double z, int rot) {
        this.unitManager.placePet(pet, x, y, z, rot);
    }

    public Pet removePet(int petId) {
        return this.unitManager.removePet(petId);
    }

    public boolean hasHabbosAt(int x, int y) {
        return this.unitManager.hasHabbosAt(x, y);
    }

    public boolean hasPetsAt(int x, int y) {
        return this.unitManager.hasPetsAt(x, y);
    }

    public Set<Bot> getBotsAt(RoomTile tile) {
        return this.unitManager.getBotsAt(tile);
    }

    public Set<Pet> getPetsAt(RoomTile tile) {
        return this.unitManager.getPetsAt(tile);
    }

    public Set<Habbo> getHabbosAt(short x, short y) {
        return this.unitManager.getHabbosAt(x, y);
    }

    public Set<Habbo> getHabbosAt(RoomTile tile) {
        return this.unitManager.getHabbosAt(tile);
    }

    public Set<RoomUnit> getHabbosAndBotsAt(short x, short y) {
        return this.unitManager.getHabbosAndBotsAt(x, y);
    }

    public Set<RoomUnit> getHabbosAndBotsAt(RoomTile tile) {
        return this.unitManager.getHabbosAndBotsAt(tile);
    }

    public Set<Habbo> getHabbosOnItem(HabboItem item) {
        return this.unitManager.getHabbosOnItem(item);
    }

    public Set<Bot> getBotsOnItem(HabboItem item) {
        return this.unitManager.getBotsOnItem(item);
    }

    public void teleportHabboToItem(Habbo habbo, HabboItem item) {
        this.unitManager.teleportHabboToItem(habbo, item);
    }

    public void teleportHabboToLocation(Habbo habbo, short x, short y) {
        this.unitManager.teleportHabboToLocation(habbo, x, y);
    }

    public void teleportRoomUnitToItem(RoomUnit roomUnit, HabboItem item) {
        this.unitManager.teleportRoomUnitToItem(roomUnit, item);
    }

    public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y) {
        this.unitManager.teleportRoomUnitToLocation(roomUnit, x, y);
    }

    public void teleportRoomUnitToLocation(RoomUnit roomUnit, short x, short y, double z) {
        this.unitManager.teleportRoomUnitToLocation(roomUnit, x, y, z);
    }

    public void muteHabbo(Habbo habbo, int minutes) {
        this.chatManager.muteHabbo(habbo, minutes);
        this.sendComposer(new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.MUTED).compose());
    }

    public void unmuteHabbo(Habbo habbo) {
        this.chatManager.unmuteHabbo(habbo);
        this.sendComposer(new RoomUserIgnoredComposer(habbo, RoomUserIgnoredComposer.UNIGNORED).compose());
    }

    public boolean isMuted(Habbo habbo) {
        return this.chatManager.isMuted(habbo);
    }

    public void habboEntered(Habbo habbo) {
        this.unitManager.habboEntered(habbo);
    }

    public void floodMuteHabbo(Habbo habbo, int timeOut) {
        this.chatManager.floodMuteHabbo(habbo, timeOut);
    }

    public void talk(Habbo habbo, RoomChatMessage roomChatMessage, RoomChatType chatType) {
        this.chatManager.talk(habbo, roomChatMessage, chatType);
    }

    public void talk(
            final Habbo habbo, final RoomChatMessage roomChatMessage, RoomChatType chatType, boolean ignoreWired) {
        this.chatManager.talk(habbo, roomChatMessage, chatType, ignoreWired);
    }

    public Set<RoomTile> getLockedTiles() {
        return this.itemManager.getLockedTiles();
    }

    @Deprecated
    public Set<HabboItem> getItemsAt(int x, int y) {
        return this.itemManager.getItemsAt(x, y);
    }

    public Set<HabboItem> getItemsAt(RoomTile tile) {
        return this.itemManager.getItemsAt(tile);
    }

    public Set<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
        return this.itemManager.getItemsAt(tile, returnOnFirst);
    }

    public Set<HabboItem> getItemsAt(int x, int y, double minZ) {
        return this.itemManager.getItemsAt(x, y, minZ);
    }

    public Set<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
        return this.itemManager.getItemsAt(type, x, y);
    }

    public boolean hasItemsAt(int x, int y) {
        return this.itemManager.hasItemsAt(x, y);
    }

    public HabboItem getTopItemAt(int x, int y) {
        return this.itemManager.getTopItemAt(x, y);
    }

    public HabboItem getTopItemAt(int x, int y, HabboItem exclude) {
        return this.itemManager.getTopItemAt(x, y, exclude);
    }

    public HabboItem getTopItemAt(Set<RoomTile> tiles, HabboItem exclude) {
        return this.itemManager.getTopItemAt(tiles, exclude);
    }

    public double getTopHeightAt(int x, int y) {
        return this.itemManager.getTopHeightAt(x, y);
    }

    @Deprecated
    public HabboItem getLowestChair(int x, int y) {
        return this.itemManager.getLowestChair(x, y);
    }

    public HabboItem getLowestChair(RoomTile tile) {
        return this.itemManager.getLowestChair(tile);
    }

    public HabboItem getTallestChair(RoomTile tile) {
        return this.itemManager.getTallestChair(tile);
    }

    public double getStackHeight(short x, short y, boolean calculateHeightmap, HabboItem exclude) {
        return this.tileManager.getStackHeight(x, y, calculateHeightmap, exclude);
    }

    public double getStackHeight(short x, short y, boolean calculateHeightmap) {
        return this.tileManager.getStackHeight(x, y, calculateHeightmap);
    }

    public boolean hasObjectTypeAt(Class<?> type, int x, int y) {
        return this.itemManager.hasObjectTypeAt(type, x, y);
    }

    public boolean canSitOrLayAt(int x, int y) {
        return this.tileManager.canSitOrLayAt(x, y);
    }

    public boolean canSitAt(int x, int y) {
        return this.tileManager.canSitAt(x, y);
    }

    boolean canWalkAt(RoomTile roomTile) {
        return this.tileManager.canWalkAt(roomTile);
    }

    boolean canSitAt(Set<HabboItem> items) {
        return this.tileManager.canSitAt(items);
    }

    public boolean canLayAt(int x, int y) {
        return this.tileManager.canLayAt(x, y);
    }

    boolean canLayAt(Set<HabboItem> items) {
        return this.tileManager.canLayAt(items);
    }

    public RoomTile getRandomWalkableTile() {
        return this.tileManager.getRandomWalkableTile();
    }

    public RoomTile getRandomWalkableTilesAround(RoomUnit roomUnit, RoomTile tile, int radius) {
        return this.tileManager.getRandomWalkableTilesAround(roomUnit, tile, radius);
    }

    public Habbo getHabbo(String username) {
        return this.unitManager.getHabbo(username);
    }

    public Habbo getHabbo(RoomUnit roomUnit) {
        return this.unitManager.getHabboByRoomUnit(roomUnit);
    }

    public Habbo getHabbo(int userId) {
        return this.unitManager.getHabbo(userId);
    }

    public Habbo getHabboByRoomUnitId(int roomUnitId) {
        return this.unitManager.getHabboByRoomUnitId(roomUnitId);
    }

    public void sendComposer(ServerMessage message) {
        this.messagingManager.sendComposer(message);
    }

    public void sendComposers(Collection<ServerMessage> messages) {
        this.messagingManager.sendComposers(messages);
    }

    public void sendComposerToHabbosWithRights(ServerMessage message) {
        this.messagingManager.sendComposerToHabbosWithRights(message);
    }

    public void petChat(ServerMessage message) {
        this.messagingManager.petChat(message);
    }

    public void botChat(ServerMessage message) {
        this.messagingManager.botChat(message);
    }

    public RoomRightLevels getGuildRightLevel(Habbo habbo) {
        return this.rightsManager.getGuildRightLevel(habbo);
    }

    /**
     * @deprecated Deprecated since 2.5.0. Use {@link #getGuildRightLevel(Habbo)} instead.
     */
    @Deprecated
    public int guildRightLevel(Habbo habbo) {
        return this.rightsManager.guildRightLevel(habbo);
    }

    public boolean isOwner(Habbo habbo) {
        return this.rightsManager.isOwner(habbo);
    }

    public boolean hasRights(Habbo habbo) {
        return this.rightsManager.hasRights(habbo);
    }

    public boolean hasExplicitRights(Habbo habbo) {
        return habbo != null && this.rights.contains(habbo.getHabboInfo().getId());
    }

    public int getWiredInspectMask() {
        return this.wiredAccess.inspectMask();
    }

    public int getWiredModifyMask() {
        return this.wiredAccess.modifyMask();
    }

    public boolean canInspectWired(Habbo habbo) {
        return this.wiredAccess.canInspect(habbo);
    }

    public boolean canModifyWired(Habbo habbo) {
        return this.wiredAccess.canModify(habbo);
    }

    public boolean canManageWiredSettings(Habbo habbo) {
        return this.wiredAccess.canManage(habbo);
    }

    public boolean saveWiredSettings(int inspectMask, int modifyMask) {
        return this.wiredAccess.save(inspectMask, modifyMask);
    }

    public void giveRights(Habbo habbo) {
        if (habbo == null) {
            return;
        }

        this.giveRights(habbo.getHabboInfo().getId());
    }

    public void giveRights(int userId) {
        this.rightsManager.giveRights(userId);
        this.wiredAccess.publish();
    }

    public void removeRights(int userId) {
        this.rightsManager.removeRights(userId);
        this.wiredAccess.publish();
    }

    public void removeAllRights() {
        this.rightsManager.removeAllRights();
        this.wiredAccess.publish();
    }

    void refreshRightsInRoom() {
        this.rightsManager.refreshRightsInRoom();
    }

    public void refreshRightsForHabbo(Habbo habbo) {
        this.rightsManager.refreshRightsForHabbo(habbo);
    }

    public Map<Integer, String> getUsersWithRights() {
        return this.rightsManager.getUsersWithRights();
    }

    public void unbanHabbo(int userId) {
        this.rightsManager.unbanHabbo(userId);
    }

    public boolean isBanned(Habbo habbo) {
        return this.rightsManager.isBanned(habbo);
    }

    public Int2ObjectMap<RoomBan> getBannedHabbos() {
        return this.bannedHabbos;
    }

    public void addRoomBan(RoomBan roomBan) {
        this.rightsManager.addRoomBan(roomBan);
    }

    public void makeSit(Habbo habbo) {
        this.posture.makeSit(habbo);
    }

    public void makeStand(Habbo habbo) {
        this.posture.makeStand(habbo);
    }

    public void giveEffect(Habbo habbo, int effectId, int duration) {
        this.unitManager.giveEffect(habbo, effectId, duration);
    }

    public void giveEffect(RoomUnit roomUnit, int effectId, int duration) {
        this.unitManager.giveEffect(roomUnit, effectId, duration);
    }

    public void giveHandItem(Habbo habbo, int handItem) {
        this.unitManager.giveHandItem(habbo, handItem);
    }

    public void giveHandItem(RoomUnit roomUnit, int handItem) {
        this.unitManager.giveHandItem(roomUnit, handItem);
    }

    public void updateItem(HabboItem item) {
        this.itemManager.updateItem(item);
    }

    public void updateItemState(HabboItem item) {
        this.itemManager.updateItemState(item);
    }

    public int getUserFurniCount(int userId) {
        return this.itemManager.getFurniOwnerCount().get(userId);
    }

    public int getUserUniqueFurniCount(int userId) {
        return this.itemManager.getUserUniqueFurniCount(userId);
    }

    public void ejectUserFurni(int userId) {
        this.itemManager.ejectUserFurni(userId);
    }

    public void ejectUserItem(HabboItem item) {
        this.itemManager.ejectUserItem(item);
    }

    public void ejectAll() {
        this.itemManager.ejectAll();
    }

    public void ejectAll(Habbo habbo) {
        this.itemManager.ejectAll(habbo);
    }

    public void refreshGuild(Guild guild) {
        this.guildService.refresh(guild);
    }

    public void refreshGuildColors(Guild guild) {
        this.guildService.refreshColors(guild);
    }

    public void refreshGuildRightsInRoom() {
        this.guildService.refreshRights();
    }

    public void idle(Habbo habbo) {
        this.unitManager.idle(habbo);
    }

    public void unIdle(Habbo habbo) {
        this.unitManager.unIdle(habbo);
    }

    public void dance(Habbo habbo, DanceType danceType) {
        this.unitManager.dance(habbo, danceType);
    }

    public void dance(RoomUnit unit, DanceType danceType) {
        this.unitManager.dance(unit, danceType);
    }

    public void addToWordFilter(String word) {
        this.chatManager.addToWordFilter(word);
    }

    public void removeFromWordFilter(String word) {
        this.chatManager.removeFromWordFilter(word);
    }

    public void handleWordQuiz(Habbo habbo, String answer) {
        this.wordQuizManager.handleWordQuiz(habbo, answer);
    }

    public void startWordQuiz(String question, int duration) {
        this.wordQuizManager.startWordQuiz(question, duration);
    }

    public boolean hasActiveWordQuiz() {
        return this.wordQuizManager.hasActiveWordQuiz();
    }

    public boolean hasVotedInWordQuiz(Habbo habbo) {
        return this.wordQuizManager.hasVotedInWordQuiz(habbo);
    }

    public void alert(String message) {
        this.messagingManager.alert(message);
    }

    public int itemCount() {
        return this.itemManager.itemCount();
    }

    public void setJukeBoxActive(boolean jukeBoxActive) {
        this.jukeboxActive = jukeBoxActive;
        this.needsUpdate = true;
    }

    boolean isJukeboxActive() {
        return this.jukeboxActive;
    }

    public boolean isHideWired() {
        return this.hideWired;
    }

    public void setHideWired(boolean hideWired) {
        this.wiredVisibility.setHidden(hideWired);
    }

    void updateHideWiredState(boolean hideWired) {
        this.hideWired = hideWired;
    }

    public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile, int rotation) {
        return this.itemManager.canPlaceFurnitureAt(item, habbo, tile, rotation);
    }

    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
        return this.itemManager.furnitureFitsAt(tile, item, rotation);
    }

    public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation, boolean checkForUnits) {
        return this.itemManager.furnitureFitsAt(tile, item, rotation, checkForUnits);
    }

    public FurnitureMovementError furnitureFitsAtWithPhysics(
            RoomTile tile, HabboItem item, int rotation, boolean checkForUnits, WiredMovementPhysics physics) {
        return this.itemManager.furnitureFitsAtWithPhysics(tile, item, rotation, checkForUnits, physics);
    }

    public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation, Habbo owner) {
        return this.itemManager.placeFloorFurniAt(item, tile, rotation, owner);
    }

    public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
        return this.itemManager.placeWallFurniAt(item, wallPosition, owner);
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, Habbo actor) {
        return this.itemManager.moveFurniTo(item, tile, rotation, actor);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates) {
        return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
        return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
    }

    public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor) {
        return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, true, true);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates) {
        return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, sendUpdates, true);
    }

    public FurnitureMovementError moveFurniTo(
            HabboItem item,
            RoomTile tile,
            int rotation,
            double z,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits) {
        return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, sendUpdates, checkForUnits);
    }

    public FurnitureMovementError moveFurniToWithPhysics(
            HabboItem item,
            RoomTile tile,
            int rotation,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits,
            WiredMovementPhysics physics) {
        return this.itemManager.moveFurniToWithPhysics(
                item, tile, rotation, actor, sendUpdates, checkForUnits, physics);
    }

    public FurnitureMovementError moveFurniToWithPhysics(
            HabboItem item,
            RoomTile tile,
            int rotation,
            double z,
            Habbo actor,
            boolean sendUpdates,
            boolean checkForUnits,
            WiredMovementPhysics physics) {
        return this.itemManager.moveFurniToWithPhysics(
                item, tile, rotation, z, actor, sendUpdates, checkForUnits, physics);
    }

    public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
        return this.itemManager.slideFurniTo(item, tile, rotation);
    }

    public Set<RoomUnit> getRoomUnits() {
        return this.unitManager.getRoomUnits();
    }

    public Set<RoomUnit> getRoomUnits(RoomTile atTile) {
        return this.unitManager.getRoomUnits(atTile);
    }

    public Collection<RoomUnit> getRoomUnitsAt(RoomTile tile) {
        return this.unitManager.getRoomUnitsAt(tile);
    }

    public long getEstimatedMemoryUsage() {
        return RoomMemoryEstimator.estimate(this);
    }
}
