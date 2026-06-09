package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.guilds.GuildRank;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.*;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameTimer;
import com.eu.habbo.habbohotel.items.interactions.games.InteractionGameUpCounter;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.users.DanceType;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredUserActionType;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredMovementPhysics;
import com.eu.habbo.messages.ISerialize;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.guilds.GuildInfoComposer;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.rooms.HideDoorbellComposer;
import com.eu.habbo.messages.outgoing.rooms.UpdateStackHeightComposer;
import com.eu.habbo.messages.outgoing.rooms.items.*;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserIgnoredComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRoomSettingsDataComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.events.furniture.FurniturePickedUpEvent;
import com.eu.habbo.plugin.events.rooms.RoomLoadedEvent;
import com.eu.habbo.plugin.events.rooms.RoomUnloadedEvent;
import com.eu.habbo.plugin.events.rooms.RoomUnloadingEvent;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.THashMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Room implements Comparable<Room>, ISerialize, Runnable {

  private static final Logger LOGGER = LoggerFactory.getLogger(Room.class);

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
  private static final TIntObjectHashMap<RoomMoodlightData> defaultMoodData = new TIntObjectHashMap<>();
  //Configuration. Loaded from database & updated accordingly.
  public static boolean HABBO_CHAT_DELAY = false;
  public static int MAXIMUM_BOTS = 10;
  public static int MAXIMUM_PETS = 10;
  public static int MAXIMUM_FURNI = 2500;
  public static int MAXIMUM_POSTITNOTES = 200;
  public static int HAND_ITEM_TIME = 10;
  public static int IDLE_CYCLES = 240;
  public static int IDLE_CYCLES_KICK = 480;
  public static String PREFIX_FORMAT = "[<font color=\"%color%\">%prefix%</font>] ";
  public static int ROLLERS_MAXIMUM_ROLL_AVATARS = 1;
  public static boolean MUTEAREA_CAN_WHISPER = false;
  public static double MAXIMUM_FURNI_HEIGHT = 40d;
  public static final int WIRED_ACCESS_EVERYONE = 1;
  public static final int WIRED_ACCESS_USERS_WITH_RIGHTS = 2;
  public static final int WIRED_ACCESS_GROUP_MEMBERS = 4;
  public static final int WIRED_ACCESS_GROUP_ADMINS = 8;
  public static final int WIRED_ACCESS_ALLOWED_INSPECT_MASK = WIRED_ACCESS_EVERYONE | WIRED_ACCESS_USERS_WITH_RIGHTS | WIRED_ACCESS_GROUP_MEMBERS | WIRED_ACCESS_GROUP_ADMINS;
  public static final int WIRED_ACCESS_ALLOWED_MODIFY_MASK = WIRED_ACCESS_USERS_WITH_RIGHTS | WIRED_ACCESS_GROUP_MEMBERS | WIRED_ACCESS_GROUP_ADMINS;
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
  private final TIntArrayList rights;
  private final TIntIntHashMap mutedHabbos;
  private final TIntObjectHashMap<RoomBan> bannedHabbos;
  private final Set<Game> games;
  private final TIntObjectMap<RoomMoodlightData> moodlightData;
  public volatile double lastCycleCpuMs = 0.0;
  public volatile String lastCycleThread = "N/A";

  private final Object loadLock = new Object();
  //Use appropriately. Could potentially cause memory leaks when used incorrectly.
  public volatile boolean preventUnloading = false;
  public volatile boolean preventUncaching = false;
  public Set<ServerMessage> scheduledComposers = ConcurrentHashMap.newKeySet();
  public final java.util.concurrent.ConcurrentLinkedQueue<Runnable> scheduledTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();
  public String wordQuiz = "";
  public int noVotes = 0;
  public int yesVotes = 0;
  public int wordQuizEnd = 0;
  public ScheduledFuture<?> roomCycleTask;
  private int id;
  private int ownerId;
  private String ownerName;
  private String name;
  private String description;
  private RoomLayout layout;
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
  // Read every room cycle (processBots/processPets) but written from settings/
  // admin packet handlers on another thread — volatile for cross-thread visibility.
  private volatile boolean allowPets;
  private volatile boolean allowPetsEat;
  private boolean allowWalkthrough;
  private volatile boolean allowBotsWalk;
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
  private boolean promoted;
  private int tradeMode;
  private boolean moveDiagonally;
  private boolean allowUnderpass;
  private boolean jukeboxActive;
  private boolean hideWired;
  private boolean buildersClubTrialLocked;
  private RoomState buildersClubOriginalState;
  private RoomPromotion promotion;
  private volatile boolean needsUpdate;
  private volatile boolean loaded;
  private volatile boolean preLoaded;
  private volatile boolean loadingInProgress;
  private volatile CompletableFuture<Void> loadingFuture;
  private int rollerSpeed;
  private int lastTimerReset = Emulator.getIntUnixTimestamp();
  private volatile boolean muted;
  private RoomSpecialTypes roomSpecialTypes;
  private TraxManager traxManager;
  private final Object wiredSettingsLock = new Object();
  private volatile boolean wiredSettingsLoaded;
  private int wiredInspectMask = WIRED_ACCESS_DEFAULT_INSPECT_MASK;
  private int wiredModifyMask = WIRED_ACCESS_DEFAULT_MODIFY_MASK;
  private boolean youtubeEnabled = false;
  private boolean soundboardEnabled = false;
  private String youtubeCurrentVideo = "";
  private String youtubeSenderName = "";
  private final java.util.List<String> youtubePlaylist = new java.util.concurrent.CopyOnWriteArrayList<>();
  private final java.util.Set<Integer> youtubeWatchers = java.util.concurrent.ConcurrentHashMap.newKeySet();

  public boolean isYoutubeEnabled() { return this.youtubeEnabled; }
  public void setYoutubeEnabled(boolean enabled) { this.youtubeEnabled = enabled; }
  public boolean isSoundboardEnabled() { return this.soundboardEnabled; }
  public void setSoundboardEnabled(boolean enabled) { this.soundboardEnabled = enabled; }
  public String getYoutubeCurrentVideo() { return this.youtubeCurrentVideo; }
  public String getYoutubeSenderName() { return this.youtubeSenderName; }
  public java.util.List<String> getYoutubePlaylist() { return this.youtubePlaylist; }
  public java.util.Set<Integer> getYoutubeWatchers() { return this.youtubeWatchers; }

  public void setYoutubeVideo(String videoId, String senderName, java.util.List<String> playlist) {
    this.youtubeCurrentVideo = videoId;
    this.youtubeSenderName = senderName;
    this.youtubePlaylist.clear();
    if (playlist != null) this.youtubePlaylist.addAll(playlist);
  }

  public void clearYoutubeVideo() {
    this.youtubeCurrentVideo = "";
    this.youtubeSenderName = "";
    this.youtubePlaylist.clear();
  }

  public final THashMap<String, Object> cache;

  public Room(ResultSet set) throws SQLException {
    this.cache = new THashMap<>(1000);
    this.id = set.getInt("id");
    this.ownerId = set.getInt("owner_id");
    this.ownerName = set.getString("owner_name");
    this.name = set.getString("name");
    this.description = set.getString("description");
    this.password = set.getString("password");
    this.state = RoomState.valueOf(set.getString("state").toUpperCase());
    this.usersMax = set.getInt("users_max");
    this.score = set.getInt("score");
    this.category = set.getInt("category");
    this.floorPaint = set.getString("paper_floor") == null ? "0.0" : set.getString("paper_floor");
    this.wallPaint = set.getString("paper_wall") == null ? "0.0" : set.getString("paper_wall");
    this.backgroundPaint = set.getString("paper_landscape") == null ? "0.0" : set.getString("paper_landscape");
    this.wallSize = set.getInt("thickness_wall");
    this.wallHeight = set.getInt("wall_height");
    this.floorSize = set.getInt("thickness_floor");
    this.tags = set.getString("tags");
    this.publicRoom = set.getBoolean("is_public");
    this.staffPromotedRoom = set.getBoolean("is_staff_picked");
    this.allowPets = set.getBoolean("allow_other_pets");
    this.allowPetsEat = set.getBoolean("allow_other_pets_eat");
    this.allowWalkthrough = set.getBoolean("allow_walkthrough");
    this.hideWall = set.getBoolean("allow_hidewall");
    try { this.youtubeEnabled = set.getBoolean("youtube_enabled"); } catch (Exception e) { this.youtubeEnabled = false; }
    try { this.soundboardEnabled = set.getBoolean("soundboard_enabled"); } catch (Exception e) { this.soundboardEnabled = false; }
    this.chatMode = set.getInt("chat_mode");
    this.chatWeight = set.getInt("chat_weight");
    this.chatSpeed = set.getInt("chat_speed");
    this.chatDistance = set.getInt("chat_hearing_distance");
    this.chatProtection = set.getInt("chat_protection");
    this.muteOption = set.getInt("who_can_mute");
    this.kickOption = set.getInt("who_can_kick");
    this.banOption = set.getInt("who_can_ban");
    this.pollId = set.getInt("poll_id");
    this.guild = set.getInt("guild_id");
    this.rollerSpeed = set.getInt("roller_speed");
    this.overrideModel = set.getString("override_model").equals("1");
    this.layoutName = set.getString("model");
    this.promoted = set.getString("promoted").equals("1");
    this.jukeboxActive = set.getString("jukebox_active").equals("1");
    this.hideWired = set.getString("hidewired").equals("1");
    this.buildersClubTrialLocked = set.getBoolean("builders_club_trial_locked");

    String buildersClubOriginalState = set.getString("builders_club_original_state");

    if (buildersClubOriginalState != null && !buildersClubOriginalState.isEmpty()) {
      try {
        this.buildersClubOriginalState = RoomState.valueOf(buildersClubOriginalState.toUpperCase());
      } catch (IllegalArgumentException e) {
        this.buildersClubOriginalState = RoomState.OPEN;
      }
    } else {
      this.buildersClubOriginalState = RoomState.OPEN;
    }

    this.bannedHabbos = new TIntObjectHashMap<>();

    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
      // Load bans eagerly (needed for entry check before loadData)
      this.loadBans(connection);
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }

    this.tradeMode = set.getInt("trade_mode");
    this.moveDiagonally = set.getString("move_diagonally").equals("1");
    this.allowUnderpass = set.getString("allow_underpass").equals("1");

    this.preLoaded = true;
    this.allowBotsWalk = true;
    this.allowEffects = true;
    this.moodlightData = new TIntObjectHashMap<>(defaultMoodData);

    for (String s : set.getString("moodlight_data").split(";")) {
      RoomMoodlightData data = RoomMoodlightData.fromString(s);
      this.moodlightData.put(data.getId(), data);
    }

    this.mutedHabbos = new TIntIntHashMap();
    this.games = ConcurrentHashMap.newKeySet();

    this.rights = new TIntArrayList();
    this.userVotes = new ArrayList<>();

    // Initialize managers
    this.initializeManagers();
  }

  /**
   * Initializes all manager instances for this room.
   */
  private void initializeManagers() {
    this.tileManager = new RoomTileManager(this);
    this.gameManager = new RoomGameManager(this);
    this.tradeManager = new RoomTradeManager(this);
    this.promotionManager = new RoomPromotionManager(this);
    this.wordQuizManager = new RoomWordQuizManager(this);
    this.rightsManager = new RoomRightsManager(this);
    this.unitManager = new RoomUnitManager(this);
    this.itemManager = new RoomItemManager(this);
    this.chatManager = new RoomChatManager(this);
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
    synchronized (this.loadLock) {
      return this.loadingInProgress;
    }
  }

  /**
   * Checks if the room data is loaded or is currently being loaded.
   */
  public boolean isLoadedOrLoading() {
    synchronized (this.loadLock) {
      return this.loaded || this.loadingInProgress;
    }
  }

  /**
   * Starts loading room data asynchronously in the background.
   * This allows the room to start loading before the user fully enters,
   * reducing perceived load time.
   */
  public void startBackgroundLoad() {
    synchronized (this.loadLock) {
      if (this.loaded || this.loadingInProgress || !this.preLoaded) {
        return;
      }

      this.loadingInProgress = true;
      this.loadingFuture = CompletableFuture.runAsync(() -> {
        this.loadDataInternal();
      }, Emulator.getThreading().getService());
    }
  }

  /**
   * Waits for background loading to complete if it's in progress.
   * If loading hasn't started yet, starts loading synchronously.
   */
  public void waitForLoad() {
    CompletableFuture<Void> future;
    synchronized (this.loadLock) {
      if (this.loaded) {
        return;
      }
      future = this.loadingFuture;
    }

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
    CompletableFuture<Void> futureToWait = null;
    boolean shouldLoad = false;

    synchronized (this.loadLock) {
      if (this.loadingInProgress) {
        // Get the future to wait on outside the lock
        futureToWait = this.loadingFuture;
      } else if (this.preLoaded && !this.loaded) {
        this.loadingInProgress = true;
        shouldLoad = true;
      }
    }

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
    if (shouldLoad) {
      this.loadDataInternal();
    }
  }

  /**
   * Internal method that performs the actual room data loading.
   * Uses parallel loading for independent operations to reduce total load time.
   */
  private void loadDataInternal() {
    // Check if already loaded (with lock)
    synchronized (this.loadLock) {
      if (this.loaded) {
        this.loadingInProgress = false;
        return;
      }
      this.preLoaded = false;
    }

    // Perform loading WITHOUT holding the lock to avoid deadlocks
    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
      synchronized (this.roomUnitLock) {
        this.unitManager.clear();
      }

      this.roomSpecialTypes = new RoomSpecialTypes();

      // Phase 1: Load layout first (required for bots/pets positioning)
      try {
        this.loadLayout();
      } catch (Exception e) {
        LOGGER.error("Caught exception loading layout", e);
      }

      if (this.promoted) {
        CompletableFuture.runAsync(() -> {
          try (Connection promoConnection = Emulator.getDatabase().getDataSource().getConnection();
               PreparedStatement stmt = promoConnection.prepareStatement(
                       "SELECT * FROM room_promotions WHERE room_id = ? AND end_timestamp > ? LIMIT 1")) {
            stmt.setInt(1, this.id);
            stmt.setInt(2, Emulator.getIntUnixTimestamp());
            try (ResultSet promoSet = stmt.executeQuery()) {
              this.promoted = false;
              if (promoSet.next()) {
                this.promoted = true;
                this.promotion = new RoomPromotion(this, promoSet);
              }
            }
          } catch (Exception e) {
            LOGGER.error("Caught exception loading promotion", e);
          }
        }, Emulator.getThreading().getService());
      }

      CompletableFuture<Void> itemsFuture = CompletableFuture.runAsync(() -> {
        try (Connection itemConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadItems(itemConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading items", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> rightsFuture = CompletableFuture.runAsync(() -> {
        try (Connection rightsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadRights(rightsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading rights", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> wordFilterFuture = CompletableFuture.runAsync(() -> {
        try (Connection wordFilterConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadWordFilter(wordFilterConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading word filter", e);
        }
      }, Emulator.getThreading().getService());

      // Bots and pets only need layout for positioning - start them now
      CompletableFuture<Void> botsFuture = CompletableFuture.runAsync(() -> {
        try (Connection botsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadBots(botsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading bots", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> petsFuture = CompletableFuture.runAsync(() -> {
        try (Connection petsConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadPets(petsConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading pets", e);
        }
      }, Emulator.getThreading().getService());

      // Wait for items (needed for heightmap + wired)
      try {
        itemsFuture.join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for items to load", e);
      }

      // Phase 3: Heightmap and wired in parallel (both depend on items, not on each other)
      CompletableFuture<Void> heightmapFuture = CompletableFuture.runAsync(() -> {
        try {
          this.loadHeightmap();
        } catch (Exception e) {
          LOGGER.error("Caught exception loading heightmap", e);
        }
      }, Emulator.getThreading().getService());

      CompletableFuture<Void> wiredFuture = CompletableFuture.runAsync(() -> {
        try (Connection wiredConnection = Emulator.getDatabase().getDataSource().getConnection()) {
          this.loadWiredData(wiredConnection);
        } catch (Exception e) {
          LOGGER.error("Caught exception loading wired data", e);
        }
      }, Emulator.getThreading().getService());

      // Wait for all remaining operations
      try {
        CompletableFuture.allOf(rightsFuture, wordFilterFuture, botsFuture, petsFuture, heightmapFuture, wiredFuture).join();
      } catch (Exception e) {
        LOGGER.error("Error waiting for parallel room data loading", e);
      }

      this.cycleManager.resetIdleCycles();

      if (this.roomCycleTask != null) {
        this.roomCycleTask.cancel(false);
      }

      this.roomCycleTask = Emulator.getThreading().getService()
              .scheduleAtFixedRate(this, 500, 500, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      LOGGER.error("Caught exception during room load", e);
    }

    this.traxManager = new TraxManager(this);

    if (this.jukeboxActive) {
      this.traxManager.play(0);
      for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionJukeBox.class)) {
        item.setExtradata("1");
        this.updateItem(item);
      }
    }

    for (HabboItem item : this.roomSpecialTypes.getItemsOfType(InteractionFireworks.class)) {
      item.setExtradata("1");
      this.updateItem(item);
    }

    // Set loaded flag with lock
    synchronized (this.loadLock) {
      this.loaded = true;
      this.loadingInProgress = false;
      this.loadingFuture = null;
    }

    Emulator.getPluginManager().fireEvent(new RoomLoadedEvent(this));
  }

  private synchronized void loadLayout() {
    if (this.layout == null) {
      if (this.overrideModel) {
        this.layout = Emulator.getGameEnvironment().getRoomManager().loadCustomLayout(this);
      } else {
        this.layout = Emulator.getGameEnvironment().getRoomManager()
                .loadLayout(this.layoutName, this);
      }
    }
  }

  private synchronized void loadHeightmap() {
    if (this.layout != null) {
      for (short x = 0; x < this.layout.getMapSizeX(); x++) {
        for (short y = 0; y < this.layout.getMapSizeY(); y++) {
          RoomTile tile = this.layout.getTile(x, y);
          if (tile != null) {
            this.updateTile(tile);
          }
        }
      }
    } else {
      LOGGER.error("Unknown Room Layout for Room (ID: {})", this.id);
    }
  }

  private synchronized void loadItems(Connection connection) {
    this.itemManager.loadItems(connection);
  }

  private synchronized void loadWiredData(Connection connection) {
    this.itemManager.loadWiredData(connection);
  }

  private synchronized void loadBots(Connection connection) {
    this.unitManager.clearBots();

    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT users.username AS owner_name, bots.* FROM bots INNER JOIN users ON bots.user_id = users.id WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          Bot b = Emulator.getGameEnvironment().getBotManager().loadBot(set);

          if (b != null) {
            b.setRoom(this);
            b.setRoomUnit(new RoomUnit());
            b.getRoomUnit().setPathFinderRoom(this);
            b.getRoomUnit()
                    .setLocation(this.layout.getTile((short) set.getInt("x"), (short) set.getInt("y")));
            if (b.getRoomUnit().getCurrentLocation() == null) {
              b.getRoomUnit().setLocation(this.getLayout().getDoorTile());
              b.getRoomUnit()
                      .setRotation(RoomUserRotation.fromValue(this.getLayout().getDoorDirection()));
            } else {
              b.getRoomUnit().setZ(set.getDouble("z"));
              b.getRoomUnit().setPreviousLocationZ(set.getDouble("z"));
              b.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
            }
            b.getRoomUnit().setRoomUnitType(RoomUnitType.BOT);
            b.getRoomUnit().setDanceType(DanceType.values()[set.getInt("dance")]);
            //b.getRoomUnit().setCanWalk(set.getBoolean("freeroam"));
            b.getRoomUnit().setInRoom(true);
            this.giveEffect(b.getRoomUnit(), set.getInt("effect"), Integer.MAX_VALUE);
            this.addBot(b);
          }
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private synchronized void loadPets(Connection connection) {
    this.unitManager.clearPets();

    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT users.username as pet_owner_name, users_pets.* FROM users_pets INNER JOIN users ON users_pets.user_id = users.id WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          try {
            Pet pet = PetManager.loadPet(set);
            pet.setRoom(this);
            pet.setRoomUnit(new RoomUnit());
            pet.getRoomUnit().setPathFinderRoom(this);
            pet.getRoomUnit()
                    .setLocation(this.layout.getTile((short) set.getInt("x"), (short) set.getInt("y")));
            if (pet.getRoomUnit().getCurrentLocation() == null) {
              pet.getRoomUnit().setLocation(this.getLayout().getDoorTile());
              pet.getRoomUnit()
                      .setRotation(RoomUserRotation.fromValue(this.getLayout().getDoorDirection()));
            } else {
              pet.getRoomUnit().setZ(set.getDouble("z"));
              pet.getRoomUnit().setRotation(RoomUserRotation.values()[set.getInt("rot")]);
            }
            pet.getRoomUnit().setRoomUnitType(RoomUnitType.PET);
            pet.getRoomUnit().setCanWalk(true);
            this.addPet(pet);

            this.getFurniOwnerNames().put(pet.getUserId(), set.getString("pet_owner_name"));
          } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
          }
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private synchronized void loadWordFilter(Connection connection) {
    this.chatManager.loadWordFilter(connection);
  }

  public void updateTile(RoomTile tile) {
    this.tileManager.updateTile(tile);
  }

  public void updateTiles(THashSet<RoomTile> tiles) {
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
    if (item == null) {
      return;
    }

    boolean trackedBuildersClubItem = BuildersClubRoomSupport.isTrackedItem(item.getId());

    if (Emulator.getPluginManager().isRegistered(FurniturePickedUpEvent.class, true)) {
      Event furniturePickedUpEvent = new FurniturePickedUpEvent(item, picker);
      Emulator.getPluginManager().fireEvent(furniturePickedUpEvent);

      if (furniturePickedUpEvent.isCancelled()) {
        return;
      }
    }

    this.removeHabboItem(item.getId());
    item.onPickUp(this);
    item.setRoomId(0);
    item.needsUpdate(true);

    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
      this.sendComposer(new RemoveFloorItemComposer(item).compose());

      THashSet<RoomTile> updatedTiles = new THashSet<>();
      Rectangle rectangle = RoomLayout.getRectangle(item.getX(), item.getY(),
              item.getBaseItem().getWidth(), item.getBaseItem().getLength(), item.getRotation());

      for (short x = (short) rectangle.x; x < rectangle.x + rectangle.getWidth(); x++) {
        for (short y = (short) rectangle.y; y < rectangle.y + rectangle.getHeight(); y++) {
          double stackHeight = this.getStackHeight(x, y, false);
          RoomTile tile = this.layout.getTile(x, y);

          if (tile != null) {
            tile.setStackHeight(stackHeight);
            updatedTiles.add(tile);
          }
        }
      }
      this.sendComposer(new UpdateStackHeightComposer(this, updatedTiles).compose());
      this.updateTiles(updatedTiles);
      for (RoomTile tile : updatedTiles) {
        this.updateHabbosAt(tile.x, tile.y);
        this.updateBotsAt(tile.x, tile.y);
      }
    } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
      this.sendComposer(new RemoveWallItemComposer(item).compose());
    }

    if (trackedBuildersClubItem) {
      Emulator.getGameEnvironment().getItemManager().deleteItem(item);
      return;
    }

    Habbo habbo = (picker != null && picker.getHabboInfo().getId() == item.getId() ? picker
            : Emulator.getGameServer().getGameClientManager().getHabbo(item.getUserId()));
    if (!trackedBuildersClubItem && habbo != null) {
      habbo.getInventory().getItemsComponent().addItem(item);
      habbo.getClient().sendResponse(new AddHabboItemComposer(item));
      habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }
    Emulator.getThreading().run(item);
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
    HabboItem item = this.getTopItemAt(roomUnit.getX(), roomUnit.getY());

    if ((item == null && !roomUnit.cmdSit) || (item != null && !item.getBaseItem().allowSit())) {
      roomUnit.removeStatus(RoomUnitStatus.SIT);
    }

    double oldZ = roomUnit.getZ();

    if (item != null) {
      if (item.getBaseItem().allowSit()) {
        roomUnit.setZ(item.getZ());
      } else {
        roomUnit.setZ(item.getZ() + Item.getCurrentHeight(item));
      }

      if (oldZ != roomUnit.getZ()) {
        this.scheduledTasks.add(() -> {
          try {
            item.onWalkOn(roomUnit, Room.this, null);
          } catch (Exception e) {

          }
        });
      }
    }

    this.sendComposer(new RoomUserStatusComposer(roomUnit).compose());
  }

  public void updateHabbosAt(short x, short y) {
    this.unitManager.updateHabbosAt(x, y);
  }

  public void updateHabbosAt(short x, short y, THashSet<Habbo> habbos) {
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
    synchronized (this.loadLock) {
      if (this.preventUnloading) {
        return;
      }

      if (Emulator.getPluginManager().fireEvent(new RoomUnloadingEvent(this)).isCancelled()) {
        return;
      }

      if (this.loaded) {
        // Set loaded to false FIRST to prevent re-entry and ensure cycle stops
        this.loaded = false;

        try {
          if (this.traxManager != null && !this.traxManager.disposed()) {
            this.traxManager.dispose();
          }

          if (this.roomCycleTask != null) {
            this.roomCycleTask.cancel(false);
            this.roomCycleTask = null;
          }
          this.scheduledTasks.clear();
          this.scheduledComposers.clear();

          synchronized (this.mutedHabbos) {
            this.mutedHabbos.clear();
          }

          for (InteractionGameTimer timer : this.getRoomSpecialTypes().getGameTimers().values()) {
            if (timer instanceof InteractionGameUpCounter) {
              ((InteractionGameUpCounter) timer).resetOnRoomUnload(this);
            } else {
              timer.setRunning(false);
            }
          }

          for (Game game : this.games) {
            game.dispose();
          }
          this.games.clear();

          removeAllPets(ownerId);

          this.itemManager.saveAllPendingItems();

          // Unregister all wired tickables for this room from the tick service
          com.eu.habbo.habbohotel.wired.core.WiredManager.unregisterRoomTickables(this);

          if (this.roomSpecialTypes != null) {
            this.roomSpecialTypes.dispose();
          }

          // Clear wired engine caches for this room
          if (com.eu.habbo.habbohotel.wired.core.WiredManager.getStackIndex() != null) {
            com.eu.habbo.habbohotel.wired.core.WiredManager.getStackIndex().invalidateAll(this);
          }
          if (com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine() != null) {
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomRecursionDepth(this.id);
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomRateLimiters(this.id);
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomBan(this.id);
            com.eu.habbo.habbohotel.wired.core.WiredManager.getEngine().clearRoomDiagnostics(this.id);
          }

          // Drop this room's shared wired-variable assignment caches (otherwise
          // they accrue per (room, item, user) for the JVM lifetime).
          com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredVariableReferenceSupport.invalidateRoom(this.id);

          this.itemManager.clear();

          this.unitManager.clearQueue();

          for (Habbo habbo : this.getCurrentHabbos().values()) {
            Emulator.getGameEnvironment().getRoomManager().leaveRoom(habbo, this);
          }

          this.sendComposer(new HotelViewComposer().compose());

          // Save bots BEFORE clearing - must happen before unitManager.clear()
          TIntObjectIterator<Bot> botIterator = this.getCurrentBots().iterator();

          for (int i = this.getCurrentBots().size(); i-- > 0; ) {
            try {
              botIterator.advance();
              botIterator.value().needsUpdate(true);
              botIterator.value().run();  // Run synchronously to ensure DB is updated before room reload
            } catch (NoSuchElementException e) {
              LOGGER.error("Caught exception", e);
              break;
            }
          }

          // Save ALL remaining pets (including owner's pets) BEFORE clearing
          TIntObjectIterator<Pet> petIterator = this.getCurrentPets().iterator();
          for (int i = this.getCurrentPets().size(); i-- > 0; ) {
            try {
              petIterator.advance();
              petIterator.value().needsUpdate = true;
              petIterator.value().run();  // Run synchronously to ensure DB is updated before room reload
            } catch (NoSuchElementException e) {
              LOGGER.error("Caught exception", e);
              break;
            }
          }

          this.unitManager.clear();
          this.unitManager.clearBots();
          this.unitManager.clearPets();
        } catch (Exception e) {
          LOGGER.error("Caught exception", e);
        }
      }

      try {
        this.wordQuiz = "";
        this.yesVotes = 0;
        this.noVotes = 0;
        this.updateDatabaseUserCount();
        this.preLoaded = true;
        this.layout = null;
      } catch (Exception e) {
        LOGGER.error("Caught exception", e);
      }
    }

    Emulator.getPluginManager().fireEvent(new RoomUnloadedEvent(this));
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
    message.appendInt(this.id);
    message.appendString(this.name);
    if (this.isPublicRoom()) {
      message.appendInt(0);
      message.appendString("");
    } else {
      message.appendInt(this.ownerId);
      message.appendString(this.ownerName);
    }
    message.appendInt(this.state.getState());
    message.appendInt(this.getUserCount());
    message.appendInt(this.usersMax);
    message.appendString(this.description);
    message.appendInt(0);
    message.appendInt(this.score);
    message.appendInt(0);
    message.appendInt(this.category);

    String[] tags = Arrays.stream(this.tags.split(";")).filter(t -> !t.isEmpty())
            .toArray(String[]::new);
    message.appendInt(tags.length);
    for (String s : tags) {
      message.appendString(s);
    }

    int base = 0;

    if (this.getGuildId() > 0) {
      base = base | 2;
    }

    if (this.isPromoted()) {
      base = base | 4;
    }

    if (!this.isPublicRoom()) {
      base = base | 8;
    }

    message.appendInt(base);

    if (this.getGuildId() > 0) {
      Guild g = Emulator.getGameEnvironment().getGuildManager().getGuild(this.getGuildId());
      if (g != null) {
        message.appendInt(g.getId());
        message.appendString(g.getName());
        message.appendString(g.getBadge());
      } else {
        message.appendInt(0);
        message.appendString("");
        message.appendString("");
      }
    }

    if (this.promoted) {
      message.appendString(this.promotion.getTitle());
      message.appendString(this.promotion.getDescription());
      message.appendInt((this.promotion.getEndTimestamp() - Emulator.getIntUnixTimestamp()) / 60);
    }

  }

  @Override
  public void run() {
    synchronized (this.loadLock) {
      if (this.loaded) {
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
    }

    this.save();
  }

  public void save() {
    if (this.needsUpdate) {
      try (Connection connection = Emulator.getDatabase().getDataSource()
              .getConnection(); PreparedStatement statement = connection.prepareStatement(
              "UPDATE rooms SET name = ?, description = ?, password = ?, state = ?, users_max = ?, category = ?, score = ?, paper_floor = ?, paper_wall = ?, paper_landscape = ?, thickness_wall = ?, wall_height = ?, thickness_floor = ?, moodlight_data = ?, tags = ?, allow_other_pets = ?, allow_other_pets_eat = ?, allow_walkthrough = ?, allow_hidewall = ?, chat_mode = ?, chat_weight = ?, chat_speed = ?, chat_hearing_distance = ?, chat_protection =?, who_can_mute = ?, who_can_kick = ?, who_can_ban = ?, poll_id = ?, guild_id = ?, roller_speed = ?, override_model = ?, is_staff_picked = ?, promoted = ?, trade_mode = ?, move_diagonally = ?, owner_id = ?, owner_name = ?, jukebox_active = ?, hidewired = ?, allow_underpass = ?, youtube_enabled = ?, builders_club_trial_locked = ?, builders_club_original_state = ? WHERE id = ?")) {
        statement.setString(1, this.name);
        statement.setString(2, this.description);
        statement.setString(3, this.password);
        statement.setString(4, this.state.name().toLowerCase());
        statement.setInt(5, this.usersMax);
        statement.setInt(6, this.category);
        statement.setInt(7, this.score);
        statement.setString(8, this.floorPaint);
        statement.setString(9, this.wallPaint);
        statement.setString(10, this.backgroundPaint);
        statement.setInt(11, this.wallSize);
        statement.setInt(12, this.wallHeight);
        statement.setInt(13, this.floorSize);
        StringBuilder moodLightData = new StringBuilder();

        int id = 1;
        for (RoomMoodlightData data : this.moodlightData.valueCollection()) {
          data.setId(id);
          moodLightData.append(data.toString()).append(";");
          id++;
        }

        statement.setString(14, moodLightData.toString());
        statement.setString(15, this.tags);
        statement.setString(16, this.allowPets ? "1" : "0");
        statement.setString(17, this.allowPetsEat ? "1" : "0");
        statement.setString(18, this.allowWalkthrough ? "1" : "0");
        statement.setString(19, this.hideWall ? "1" : "0");
        statement.setInt(20, this.chatMode);
        statement.setInt(21, this.chatWeight);
        statement.setInt(22, this.chatSpeed);
        statement.setInt(23, this.chatDistance);
        statement.setInt(24, this.chatProtection);
        statement.setInt(25, this.muteOption);
        statement.setInt(26, this.kickOption);
        statement.setInt(27, this.banOption);
        statement.setInt(28, this.pollId);
        statement.setInt(29, this.guild);
        statement.setInt(30, this.rollerSpeed);
        statement.setString(31, this.overrideModel ? "1" : "0");
        statement.setString(32, this.staffPromotedRoom ? "1" : "0");
        statement.setString(33, this.promoted ? "1" : "0");
        statement.setInt(34, this.tradeMode);
        statement.setString(35, this.moveDiagonally ? "1" : "0");
        statement.setInt(36, this.ownerId);
        statement.setString(37, this.ownerName);
        statement.setString(38, this.jukeboxActive ? "1" : "0");
        statement.setString(39, this.hideWired ? "1" : "0");
        statement.setString(40, this.allowUnderpass ? "1" : "0");
        statement.setString(41, this.youtubeEnabled ? "1" : "0");
        statement.setString(42, this.buildersClubTrialLocked ? "1" : "0");
        statement.setString(43, (this.buildersClubOriginalState != null ? this.buildersClubOriginalState : RoomState.OPEN).name().toLowerCase());
        statement.setInt(44, this.id);
        statement.executeUpdate();
        this.needsUpdate = false;
      } catch (SQLException e) {
        LOGGER.error("Caught SQL exception", e);
      }
    }
  }

  /**
   * Updates the user count in the database.
   * Made public for access by RoomUnitManager.
   */
  public void updateDatabaseUserCount() {
    try (Connection connection = Emulator.getDatabase().getDataSource()
            .getConnection(); PreparedStatement statement = connection.prepareStatement(
            "UPDATE rooms SET users = ? WHERE id = ? LIMIT 1")) {
      statement.setInt(1, this.getUserCount());
      statement.setInt(2, this.id);
      statement.executeUpdate();
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
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
    this.ownerId = ownerId;
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
    if (this.guild > 0) {
      return this.guild;
    }

    try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
         PreparedStatement statement = connection.prepareStatement("SELECT guild_id FROM rooms WHERE id = ? LIMIT 1")) {
      statement.setInt(1, this.id);

      try (ResultSet set = statement.executeQuery()) {
        if (set.next()) {
          this.guild = set.getInt("guild_id");
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception resolving room guild", e);
    }

    return this.guild;
  }

  public boolean hasGuild() {
    return this.getGuildId() != 0;
  }

  public void setGuild(int guild) {
    this.guild = guild;
  }

  public String getGuildName() {
    if (this.hasGuild()) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(this.guild);

      if (guild != null) {
        return guild.getName();
      }
    }

    return "";
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
    Color color = new Color(0, 0, 0);
    TIntObjectMap<HabboItem> items = this.itemManager.getRoomItems();
    TIntObjectIterator<HabboItem> iterator = items.iterator();

    for (int i = items.size(); i > 0; i--) {
      try {
        iterator.advance();
        HabboItem object = iterator.value();

        if (object instanceof InteractionBackgroundToner) {
          String[] extraData = object.getExtradata().split(":");

          if (extraData.length == 4) {
            if (extraData[0].equalsIgnoreCase("1")) {
              return Color.getHSBColor(Integer.parseInt(extraData[1]),
                      Integer.parseInt(extraData[2]), Integer.parseInt(extraData[3]));
            }
          }
        }
      } catch (Exception e) {
      }
    }

    return color;
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

  public String[] filterAnything() {
    return new String[]{this.getOwnerName(), this.getGuildName(), this.getDescription(),
            this.getPromotionDesc()};
  }

  public long getCycleTimestamp() {
    return this.cycleManager.getCycleTimestamp();
  }

  public boolean isPromoted() {
    return this.promotionManager.isPromoted();
  }

  public RoomPromotion getPromotion() {
    return this.promotion;
  }

  public String getPromotionDesc() {
    if (this.promotion != null) {
      return this.promotion.getDescription();
    }

    return "";
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

  public TIntObjectMap<Habbo> getHabboQueue() {
    return this.unitManager.getHabboQueue();
  }

  public TIntObjectMap<String> getFurniOwnerNames() {
    return this.itemManager.getFurniOwnerNames();
  }

  public String getFurniOwnerName(int userId) {
    return this.itemManager.getFurniOwnerName(userId);
  }

  public TIntIntMap getFurniOwnerCount() {
    return this.itemManager.getFurniOwnerCount();
  }

  public TIntObjectMap<RoomMoodlightData> getMoodlightData() {
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

  public TIntObjectMap<Bot> getCurrentBots() {
    return this.unitManager.getCurrentBots();
  }

  public TIntObjectMap<Pet> getCurrentPets() {
    return this.unitManager.getCurrentPets();
  }

  public THashSet<String> getWordFilterWords() {
    return this.chatManager.getWordFilterWords();
  }

  public RoomSpecialTypes getRoomSpecialTypes() {
    return this.roomSpecialTypes;
  }

  /**
   * Alias for getRoomSpecialTypes() for shorter access.
   */
  public RoomSpecialTypes getSpecialTypes() {
    return this.roomSpecialTypes;
  }

  public boolean isPreLoaded() {
    return this.preLoaded;
  }

  public boolean isLoaded() {
    return this.loaded;
  }

  public void setNeedsUpdate(boolean needsUpdate) {
    this.needsUpdate = needsUpdate;
  }

  public TIntArrayList getRights() {
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

  public void addHabboItem(HabboItem item) {
    this.itemManager.addHabboItem(item);
  }

  public HabboItem getHabboItem(int id) {
    return this.itemManager.getHabboItem(id);
  }

  void removeHabboItem(int id) {
    this.itemManager.removeHabboItem(id);
  }


  public void removeHabboItem(HabboItem item) {
    this.itemManager.removeHabboItem(item);
  }

  public THashSet<HabboItem> getFloorItems() {
    return this.itemManager.getFloorItems();
  }

  public THashSet<HabboItem> getWallItems() {
    return this.itemManager.getWallItems();
  }

  public THashSet<HabboItem> getPostItNotes() {
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
    if (habbo == null) return;
    int userId = habbo.getHabboInfo().getId();

    // If the broadcast sender leaves, stop the broadcast for everyone
    if (!this.youtubeCurrentVideo.isEmpty()
            && habbo.getHabboInfo().getUsername().equals(this.youtubeSenderName)) {
      this.clearYoutubeVideo();
      this.sendComposer(new com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomBroadcastComposer("", "", java.util.Collections.emptyList()).compose());
    }

    if (this.youtubeWatchers.remove(userId)) {
      this.sendComposer(new com.eu.habbo.messages.outgoing.rooms.youtube.YouTubeRoomWatchersComposer(this.youtubeWatchers).compose());
    }
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

  public THashSet<Bot> getBotsAt(RoomTile tile) {
    return this.unitManager.getBotsAt(tile);
  }

  public THashSet<Pet> getPetsAt(RoomTile tile) {
    return this.unitManager.getPetsAt(tile);
  }

  public THashSet<Habbo> getHabbosAt(short x, short y) {
    return this.unitManager.getHabbosAt(x, y);
  }

  public THashSet<Habbo> getHabbosAt(RoomTile tile) {
    return this.unitManager.getHabbosAt(tile);
  }

  public THashSet<RoomUnit> getHabbosAndBotsAt(short x, short y) {
    return this.unitManager.getHabbosAndBotsAt(x, y);
  }

  public THashSet<RoomUnit> getHabbosAndBotsAt(RoomTile tile) {
    return this.unitManager.getHabbosAndBotsAt(tile);
  }

  public THashSet<Habbo> getHabbosOnItem(HabboItem item) {
    return this.unitManager.getHabbosOnItem(item);
  }

  public THashSet<Bot> getBotsOnItem(HabboItem item) {
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

  public void talk(final Habbo habbo, final RoomChatMessage roomChatMessage, RoomChatType chatType,
                   boolean ignoreWired) {
    this.chatManager.talk(habbo, roomChatMessage, chatType, ignoreWired);
  }

  public THashSet<RoomTile> getLockedTiles() {
    return this.itemManager.getLockedTiles();
  }

  @Deprecated
  public THashSet<HabboItem> getItemsAt(int x, int y) {
    return this.itemManager.getItemsAt(x, y);
  }

  public THashSet<HabboItem> getItemsAt(RoomTile tile) {
    return this.itemManager.getItemsAt(tile);
  }

  public THashSet<HabboItem> getItemsAt(RoomTile tile, boolean returnOnFirst) {
    return this.itemManager.getItemsAt(tile, returnOnFirst);
  }

  public THashSet<HabboItem> getItemsAt(int x, int y, double minZ) {
    return this.itemManager.getItemsAt(x, y, minZ);
  }

  public THashSet<HabboItem> getItemsAt(Class<? extends HabboItem> type, int x, int y) {
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

  public HabboItem getTopItemAt(THashSet<RoomTile> tiles, HabboItem exclude) {
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

  boolean canSitAt(THashSet<HabboItem> items) {
    return this.tileManager.canSitAt(items);
  }

  public boolean canLayAt(int x, int y) {
    return this.tileManager.canLayAt(x, y);
  }

  boolean canLayAt(THashSet<HabboItem> items) {
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

  private void loadRights(Connection connection) {
    this.rights.clear();
    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT user_id FROM room_rights WHERE room_id = ?")) {
      statement.setInt(1, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          this.rights.add(set.getInt("user_id"));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  private void loadBans(Connection connection) {
    this.bannedHabbos.clear();

    try (PreparedStatement statement = connection.prepareStatement(
            "SELECT users.username, users.id, room_bans.* FROM room_bans INNER JOIN users ON room_bans.user_id = users.id WHERE ends > ? AND room_bans.room_id = ?")) {
      statement.setInt(1, Emulator.getIntUnixTimestamp());
      statement.setInt(2, this.id);
      try (ResultSet set = statement.executeQuery()) {
        while (set.next()) {
          if (this.bannedHabbos.containsKey(set.getInt("user_id"))) {
            continue;
          }

          this.bannedHabbos.put(set.getInt("user_id"), new RoomBan(set));
        }
      }
    } catch (SQLException e) {
      LOGGER.error("Caught SQL exception", e);
    }
  }

  public RoomRightLevels getGuildRightLevel(Habbo habbo) {
    int guildId = this.getGuildId();

    if (guildId > 0 && habbo != null && habbo.getHabboInfo() != null) {
      Guild guild = Emulator.getGameEnvironment().getGuildManager().getGuild(guildId);

      if (guild == null) {
        return RoomRightLevels.NONE;
      }

      GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(guild.getId(), habbo.getHabboInfo().getId());

      if ((member != null) && (member.getRank() == GuildRank.ADMIN || member.getRank() == GuildRank.OWNER)) {
        return RoomRightLevels.GUILD_ADMIN;
      }

      if (guild.getRights()) {
        return RoomRightLevels.GUILD_RIGHTS;
      }
    }

    return RoomRightLevels.NONE;
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
    this.ensureWiredSettingsLoaded();
    return this.wiredInspectMask;
  }

  public int getWiredModifyMask() {
    this.ensureWiredSettingsLoaded();
    return this.wiredModifyMask;
  }

  public boolean canInspectWired(Habbo habbo) {
    if (habbo == null) {
      return false;
    }

    if (this.canManageWiredSettings(habbo)) {
      return true;
    }

    this.ensureWiredSettingsLoaded();
    return this.matchesWiredAccessMask(habbo, this.wiredInspectMask, true);
  }

  public boolean canModifyWired(Habbo habbo) {
    if (habbo == null) {
      return false;
    }

    if (this.canManageWiredSettings(habbo)) {
      return true;
    }

    this.ensureWiredSettingsLoaded();
    return this.matchesWiredAccessMask(habbo, this.wiredModifyMask, false);
  }

  public boolean canManageWiredSettings(Habbo habbo) {
    return habbo != null && this.isOwner(habbo);
  }

  public boolean saveWiredSettings(int inspectMask, int modifyMask) {
    int sanitizedInspectMask = sanitizeWiredInspectMask(inspectMask);
    int sanitizedModifyMask = sanitizeWiredModifyMask(modifyMask);
    sanitizedInspectMask |= sanitizedModifyMask;

    synchronized (this.wiredSettingsLock) {
      final int finalInspectMask = sanitizedInspectMask;
      final int finalModifyMask = sanitizedModifyMask;
      final int finalId = this.id;
      final int previousInspectMask = this.wiredInspectMask;
      final int previousModifyMask = this.wiredModifyMask;

      this.wiredInspectMask = sanitizedInspectMask;
      this.wiredModifyMask = sanitizedModifyMask;
      this.wiredSettingsLoaded = true;

      Emulator.getThreading().run(() -> {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO room_wired_settings (room_id, inspect_mask, modify_mask) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE inspect_mask = VALUES(inspect_mask), modify_mask = VALUES(modify_mask)")) {
          statement.setInt(1, finalId);
          statement.setInt(2, finalInspectMask);
          statement.setInt(3, finalModifyMask);
          statement.executeUpdate();
        } catch (SQLException e) {
          synchronized (this.wiredSettingsLock) {
            if (this.wiredInspectMask == finalInspectMask && this.wiredModifyMask == finalModifyMask) {
              this.wiredInspectMask = previousInspectMask;
              this.wiredModifyMask = previousModifyMask;
            }
          }
          LOGGER.error("Caught SQL exception while saving wired room settings", e);
        }
      });

      this.pushWiredSettingsToCurrentHabbos();
      return true;
    }
  }

  public void giveRights(Habbo habbo) {
    if (habbo == null) {
      return;
    }

    this.giveRights(habbo.getHabboInfo().getId());
  }

  public void giveRights(int userId) {
    this.rightsManager.giveRights(userId);

    if (!this.rights.contains(userId)) {
      this.rights.add(userId);
    }

    this.pushWiredSettingsToCurrentHabbos();
  }

  public void removeRights(int userId) {
    this.rightsManager.removeRights(userId);
    this.rights.remove(userId);
    this.pushWiredSettingsToCurrentHabbos();
  }

  public void removeAllRights() {
    this.rightsManager.removeAllRights();
    this.rights.clear();
    this.pushWiredSettingsToCurrentHabbos();
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

  public TIntObjectHashMap<RoomBan> getBannedHabbos() {
    return this.bannedHabbos;
  }

  private void ensureWiredSettingsLoaded() {
    if (this.wiredSettingsLoaded) {
      return;
    }

    synchronized (this.wiredSettingsLock) {
      if (this.wiredSettingsLoaded) {
        return;
      }

      this.wiredInspectMask = WIRED_ACCESS_DEFAULT_INSPECT_MASK;
      this.wiredModifyMask = WIRED_ACCESS_DEFAULT_MODIFY_MASK;

      try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
           PreparedStatement statement = connection.prepareStatement(
                   "SELECT inspect_mask, modify_mask FROM room_wired_settings WHERE room_id = ? LIMIT 1")) {
        statement.setInt(1, this.id);

        try (ResultSet set = statement.executeQuery()) {
          if (set.next()) {
            this.wiredInspectMask = sanitizeWiredInspectMask(set.getInt("inspect_mask"));
            this.wiredModifyMask = sanitizeWiredModifyMask(set.getInt("modify_mask"));
          }
        }
      } catch (SQLException e) {
        LOGGER.error("Caught SQL exception while loading wired room settings", e);
      }

      this.wiredSettingsLoaded = true;
    }
  }

  private boolean matchesWiredAccessMask(Habbo habbo, int mask, boolean allowEveryone) {
    if (habbo == null) {
      return false;
    }

    if (allowEveryone && hasWiredAccess(mask, WIRED_ACCESS_EVERYONE)) {
      return true;
    }

    if (hasWiredAccess(mask, WIRED_ACCESS_USERS_WITH_RIGHTS) && this.hasExplicitRights(habbo)) {
      return true;
    }

    if (hasWiredAccess(mask, WIRED_ACCESS_GROUP_ADMINS) && this.isRoomGroupAdmin(habbo)) {
      return true;
    }

    return hasWiredAccess(mask, WIRED_ACCESS_GROUP_MEMBERS) && this.isRoomGroupMember(habbo);
  }

  private boolean isRoomGroupMember(Habbo habbo) {
    return habbo != null && this.guild > 0 && habbo.getHabboStats().hasGuild(this.guild);
  }

  private boolean isRoomGroupAdmin(Habbo habbo) {
    if (!this.isRoomGroupMember(habbo)) {
      return false;
    }

    GuildMember member = Emulator.getGameEnvironment().getGuildManager().getGuildMember(this.guild, habbo.getHabboInfo().getId());

    if (member == null) {
      return false;
    }

    GuildRank rank = member.getRank();
    return rank == GuildRank.OWNER || rank == GuildRank.ADMIN;
  }

  private static boolean hasWiredAccess(int mask, int permissionMask) {
    return (mask & permissionMask) != 0;
  }

  private static int sanitizeWiredInspectMask(int mask) {
    int sanitizedMask = mask & WIRED_ACCESS_ALLOWED_INSPECT_MASK;

    if (hasWiredAccess(sanitizedMask, WIRED_ACCESS_GROUP_MEMBERS)) {
      sanitizedMask |= WIRED_ACCESS_GROUP_ADMINS;
    }

    return sanitizedMask;
  }

  private static int sanitizeWiredModifyMask(int mask) {
    int sanitizedMask = mask & WIRED_ACCESS_ALLOWED_MODIFY_MASK;

    if (hasWiredAccess(sanitizedMask, WIRED_ACCESS_GROUP_MEMBERS)) {
      sanitizedMask |= WIRED_ACCESS_GROUP_ADMINS;
    }

    return sanitizedMask;
  }

  private void pushWiredSettingsToCurrentHabbos() {
    for (Habbo currentHabbo : this.getCurrentHabbos().values()) {
      if (currentHabbo == null || currentHabbo.getClient() == null) {
        continue;
      }

      currentHabbo.getClient().sendResponse(new WiredRoomSettingsDataComposer(this, currentHabbo));
    }
  }

  public void addRoomBan(RoomBan roomBan) {
    this.rightsManager.addRoomBan(roomBan);
  }

  public void makeSit(Habbo habbo) {
    if (habbo.getRoomUnit() == null) {
      return;
    }

    if (habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT) || !habbo.getRoomUnit()
            .canForcePosture()) {
      return;
    }

    this.dance(habbo, DanceType.NONE);
    habbo.getRoomUnit().cmdSit = true;
    habbo.getRoomUnit().setBodyRotation(
            RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
                    - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
    habbo.getRoomUnit().setStatus(RoomUnitStatus.SIT, 0.5 + "");
    this.sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());
    WiredManager.triggerUserPerformsAction(this, habbo.getRoomUnit(), WiredUserActionType.SIT, -1);
  }

  public void makeStand(Habbo habbo) {
    if (habbo.getRoomUnit() == null) {
      return;
    }

    HabboItem item = this.getTopItemAt(habbo.getRoomUnit().getX(), habbo.getRoomUnit().getY());
    if (item == null || !item.getBaseItem().allowSit() || !item.getBaseItem().allowLay()) {
      boolean wasSittingOrLaying = habbo.getRoomUnit().hasStatus(RoomUnitStatus.SIT)
              || habbo.getRoomUnit().hasStatus(RoomUnitStatus.LAY);
      habbo.getRoomUnit().cmdStand = true;
      habbo.getRoomUnit().setBodyRotation(
              RoomUserRotation.values()[habbo.getRoomUnit().getBodyRotation().getValue()
                      - habbo.getRoomUnit().getBodyRotation().getValue() % 2]);
      habbo.getRoomUnit().removeStatus(RoomUnitStatus.SIT);
      habbo.getRoomUnit().removeStatus(RoomUnitStatus.LAY);
      this.sendComposer(new RoomUserStatusComposer(habbo.getRoomUnit()).compose());

      if (wasSittingOrLaying) {
        WiredManager.triggerUserPerformsAction(this, habbo.getRoomUnit(), WiredUserActionType.STAND, -1);
      }
    }
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
    if (this.isLoaded()) {
      if (item != null && item.getRoomId() == this.id) {
        if (item.getBaseItem() != null) {
          if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
            this.sendComposer(new FloorItemUpdateComposer(item).compose());
            this.updateTiles(this.getLayout()
                    .getTilesAt(this.layout.getTile(item.getX(), item.getY()),
                            item.getBaseItem().getWidth(), item.getBaseItem().getLength(),
                            item.getRotation()));

            if (RoomAreaHideSupport.isControllerItem(item)) {
              RoomAreaHideSupport.sendState(this, item);
            }
          } else if (item.getBaseItem().getType() == FurnitureType.WALL) {
            this.sendComposer(new WallItemUpdateComposer(item).compose());
          }
        }
      }
    }
  }

  public void updateItemState(HabboItem item) {
    if (item != null && RoomAreaHideSupport.isControllerItem(item)) {
      this.updateItem(item);
      return;
    }

    if (!item.isLimited()) {
      this.sendComposer(new ItemStateComposer(item).compose());
    } else {
      this.sendComposer(new FloorItemUpdateComposer(item).compose());
    }

    if (item.getBaseItem().getType() == FurnitureType.FLOOR) {
      if (this.layout == null) {
        return;
      }

      this.updateTiles(this.getLayout()
              .getTilesAt(this.layout.getTile(item.getX(), item.getY()), item.getBaseItem().getWidth(),
                      item.getBaseItem().getLength(), item.getRotation()));

      if (item instanceof InteractionMultiHeight) {
        ((InteractionMultiHeight) item).updateUnitsOnItem(this);
      }
    }

    if (item.getBaseItem().getType() == FurnitureType.FLOOR
            && (RoomConfInvisSupport.isControllerItem(item) || RoomConfInvisSupport.isTarget(item))) {
      RoomConfInvisSupport.sendState(this);
    }

    if (item.getBaseItem().getType() == FurnitureType.FLOOR
            && RoomHanditemBlockSupport.isControllerItem(item)) {
      RoomHanditemBlockSupport.sendState(this);
    }
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
    if (guild.getRoomId() == this.id) {
      THashSet<GuildMember> members = Emulator.getGameEnvironment().getGuildManager()
              .getGuildMembers(guild.getId());

      for (Habbo habbo : this.getHabbos()) {
        Optional<GuildMember> member = members.stream()
                .filter(m -> m.getUserId() == habbo.getHabboInfo().getId()).findAny();

        if (!member.isPresent()) {
          continue;
        }

        habbo.getClient()
                .sendResponse(new GuildInfoComposer(guild, habbo.getClient(), false, member.get()));
      }
    }

    this.refreshGuildRightsInRoom();
  }

  public void refreshGuildColors(Guild guild) {
    if (guild.getRoomId() == this.id) {
      TIntObjectMap<HabboItem> items = this.itemManager.getRoomItems();
      TIntObjectIterator<HabboItem> iterator = items.iterator();

      for (int i = items.size(); i-- > 0; ) {
        try {
          iterator.advance();
        } catch (Exception e) {
          break;
        }

        HabboItem habboItem = iterator.value();

        if (habboItem instanceof InteractionGuildFurni) {
          if (((InteractionGuildFurni) habboItem).getGuildId() == guild.getId()) {
            this.updateItem(habboItem);
          }
        }
      }
    }
  }

  public void refreshGuildRightsInRoom() {
    for (Habbo habbo : this.getHabbos()) {
      if (habbo.getHabboInfo().getCurrentRoom() == this) {
        if (habbo.getHabboInfo().getId() != this.ownerId) {
          if (!(habbo.hasPermission(Permission.ACC_ANYROOMOWNER) || habbo.hasPermission(
                  Permission.ACC_MOVEROTATE))) {
            this.refreshRightsForHabbo(habbo);
          }
        }
      }
    }
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

  public boolean isHideWired() {
    return this.hideWired;
  }

  public void setHideWired(boolean hideWired) {
    this.hideWired = hideWired;

    if (this.hideWired) {
      for (HabboItem item : this.roomSpecialTypes.getTriggers()) {
        this.sendComposer(new RemoveFloorItemComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getEffects()) {
        this.sendComposer(new RemoveFloorItemComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getConditions()) {
        this.sendComposer(new RemoveFloorItemComposer(item).compose());
      }

      for (HabboItem item : this.roomSpecialTypes.getExtras()) {
        this.sendComposer(new RemoveFloorItemComposer(item).compose());
      }
    } else {
      this.sendComposer(new RoomFloorItemsComposer(this.itemManager.getFurniOwnerNames(),
              this.roomSpecialTypes.getTriggers()).compose());
      this.sendComposer(new RoomFloorItemsComposer(this.itemManager.getFurniOwnerNames(),
              this.roomSpecialTypes.getEffects()).compose());
      this.sendComposer(new RoomFloorItemsComposer(this.itemManager.getFurniOwnerNames(),
              this.roomSpecialTypes.getConditions()).compose());
      this.sendComposer(new RoomFloorItemsComposer(this.itemManager.getFurniOwnerNames(),
              this.roomSpecialTypes.getExtras()).compose());
    }
  }

  public FurnitureMovementError canPlaceFurnitureAt(HabboItem item, Habbo habbo, RoomTile tile,
                                                    int rotation) {
    return this.itemManager.canPlaceFurnitureAt(item, habbo, tile, rotation);
  }

  public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation) {
    return this.itemManager.furnitureFitsAt(tile, item, rotation);
  }

  public FurnitureMovementError furnitureFitsAt(RoomTile tile, HabboItem item, int rotation,
                                                boolean checkForUnits) {
    return this.itemManager.furnitureFitsAt(tile, item, rotation, checkForUnits);
  }

  public FurnitureMovementError furnitureFitsAtWithPhysics(RoomTile tile, HabboItem item, int rotation,
                                                           boolean checkForUnits, WiredMovementPhysics physics) {
    return this.itemManager.furnitureFitsAtWithPhysics(tile, item, rotation, checkForUnits, physics);
  }

  public FurnitureMovementError placeFloorFurniAt(HabboItem item, RoomTile tile, int rotation,
                                                  Habbo owner) {
    return this.itemManager.placeFloorFurniAt(item, tile, rotation, owner);
  }

  public FurnitureMovementError placeWallFurniAt(HabboItem item, String wallPosition, Habbo owner) {
    return this.itemManager.placeWallFurniAt(item, wallPosition, owner);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
                                            Habbo actor) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
                                            Habbo actor, boolean sendUpdates) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation,
                                            Habbo actor, boolean sendUpdates, boolean checkForUnits) {
    return this.itemManager.moveFurniTo(item, tile, rotation, actor, sendUpdates, checkForUnits);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor) {
    return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, true, true);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates) {
    return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, sendUpdates, true);
  }

  public FurnitureMovementError moveFurniTo(HabboItem item, RoomTile tile, int rotation, double z, Habbo actor, boolean sendUpdates, boolean checkForUnits) {
    return this.itemManager.moveFurniTo(item, tile, rotation, z, actor, sendUpdates, checkForUnits);
  }

  public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation,
                                                       Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
    return this.itemManager.moveFurniToWithPhysics(item, tile, rotation, actor, sendUpdates, checkForUnits, physics);
  }

  public FurnitureMovementError moveFurniToWithPhysics(HabboItem item, RoomTile tile, int rotation, double z,
                                                       Habbo actor, boolean sendUpdates, boolean checkForUnits, WiredMovementPhysics physics) {
    return this.itemManager.moveFurniToWithPhysics(item, tile, rotation, z, actor, sendUpdates, checkForUnits, physics);
  }

  public FurnitureMovementError slideFurniTo(HabboItem item, RoomTile tile, int rotation) {
    return this.itemManager.slideFurniTo(item, tile, rotation);
  }



  public THashSet<RoomUnit> getRoomUnits() {
    return this.unitManager.getRoomUnits();
  }

  public THashSet<RoomUnit> getRoomUnits(RoomTile atTile) {
    return this.unitManager.getRoomUnits(atTile);
  }

  public Collection<RoomUnit> getRoomUnitsAt(RoomTile tile) {
    return this.unitManager.getRoomUnitsAt(tile);
  }

  public long getEstimatedMemoryUsage() {
    long bytes = 1024 * 10; // Base footprint
    if (this.itemManager != null) {
      bytes += this.itemManager.itemCount() * 512L;
    }
    bytes += this.getUserCount() * 2048L;
    if (this.layout != null) {
      bytes += this.layout.getMapSize() * 128L;
    }
    com.eu.habbo.habbohotel.wired.tick.WiredTickService wired = com.eu.habbo.habbohotel.wired.tick.WiredTickService.getInstance();
    if (wired != null) {
      bytes += wired.getTickableCount(this.getId()) * 256L;
    }
    return bytes;
  }
}
