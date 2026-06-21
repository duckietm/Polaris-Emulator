package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.catalog.CatalogItem;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveReward;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectTriggerStacks;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraExecutionLimit;
import com.eu.habbo.habbohotel.items.interactions.wired.triggers.WiredTriggerHabboClicksUser;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomWiredDisableSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboBadge;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredGiveRewardItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.api.WiredStack;
import com.eu.habbo.habbohotel.wired.migrate.WiredEvents;
import com.eu.habbo.habbohotel.wired.tick.WiredTickService;
import com.eu.habbo.habbohotel.wired.tick.WiredTickable;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.messages.outgoing.wired.WiredRewardAlertComposer;
import com.eu.habbo.plugin.EventHandler;
import com.eu.habbo.plugin.events.emulator.EmulatorLoadedEvent;
import com.eu.habbo.plugin.events.users.UserWiredRewardReceived;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.set.hash.THashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Set;

/**
 * Manager class for the wired runtime.
 * <p>
 * WiredManager is now the sole runtime entrypoint for wired execution. Legacy
 * configuration keys are still read for backwards compatibility with existing
 * databases, but they no longer switch execution back to {@code WiredHandler}.
 * </p>
 *
 * <h3>Configuration Options:</h3>
 * <ul>
 *   <li>{@code wired.engine.enabled} - Compatibility flag kept for old configs</li>
 *   <li>{@code wired.engine.exclusive} - Compatibility flag kept for old configs</li>
 *   <li>{@code wired.engine.maxStepsPerStack} - Loop protection limit</li>
 *   <li>{@code wired.engine.debug} - Verbose logging</li>
 * </ul>
 *
 * <h3>Migration Strategy:</h3>
 * <ol>
 *   <li>Set {@code wired.engine.enabled=true} to run both engines in parallel</li>
 *   <li>Test thoroughly to ensure identical behavior</li>
 *   <li>Set {@code wired.engine.exclusive=true} to disable legacy engine</li>
 *   <li>Full migration complete - WiredManager is now the only wired engine</li>
 * </ol>
 *
 * @see WiredEngine
 * @see WiredEvents
 */
public final class WiredManager {
    private static final String CACHE_LAST_ACTION_ID = "wired.last_user_action.id";
    private static final String CACHE_LAST_ACTION_PARAMETER = "wired.last_user_action.parameter";
    private static final String CACHE_LAST_ACTION_TIMESTAMP = "wired.last_user_action.timestamp";

    private static final Logger LOGGER = LoggerFactory.getLogger(WiredManager.class);

    // Configuration keys
    public static final String CONFIG_ENABLED = "wired.engine.enabled";
    public static final String CONFIG_EXCLUSIVE = "wired.engine.exclusive";
    public static final String CONFIG_MAX_STEPS = "wired.engine.maxStepsPerStack";
    public static final String CONFIG_DEBUG = "wired.engine.debug";

    // Defaults
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_EXCLUSIVE = true;
    private static final int DEFAULT_MAX_STEPS = 100;

    /** The singleton engine instance */
    private static volatile WiredEngine engine;

    /** The stack index */
    private static volatile RoomWiredStackIndex stackIndex;

    /** Whether the engine is initialized */
    private static volatile boolean initialized = false;
    private static final ThreadLocal<Integer> EVENT_HANDLING_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<DeferredEffectEvent>> DEFERRED_EFFECT_EVENTS = new ThreadLocal<>();
    private WiredManager() {
        // Static utility class
    }
    /**
     * Event handler called when the emulator is loaded.
     * Initializes the wired manager.
     */
    @EventHandler
    public static void onEmulatorLoaded(EmulatorLoadedEvent event) {
        initialize();
    }

    /**
     * Initialize the wired manager and engine.
     * Called during emulator startup.
     */
    public static synchronized void initialize() {
        if (initialized) {
            return;
        }

        LOGGER.info("Initializing Wired Manager...");

        // Load configuration
        boolean enabled = Emulator.getConfig().getBoolean(CONFIG_ENABLED, DEFAULT_ENABLED);
        boolean exclusive = Emulator.getConfig().getBoolean(CONFIG_EXCLUSIVE, DEFAULT_EXCLUSIVE);
        int maxSteps = Emulator.getConfig().getInt(CONFIG_MAX_STEPS, DEFAULT_MAX_STEPS);
        boolean debug = Emulator.getConfig().getBoolean(CONFIG_DEBUG, false);

        // Load additional configuration
        MAXIMUM_FURNI_SELECTION = Emulator.getConfig().getInt("hotel.wired.furni.selection.count", 5);
        TELEPORT_DELAY = Emulator.getConfig().getInt("wired.effect.teleport.delay", 500);

        // Set debug mode
        if (debug) {
            setDebugEnabled(true);
        }

        // Create components
        stackIndex = new RoomWiredStackIndex();
        WiredServices services = DefaultWiredServices.getInstance();
        engine = new WiredEngine(services, stackIndex, maxSteps);

        // Start the centralized tick service (50ms interval)
        WiredTickService.getInstance().start();

        initialized = true;

        if (!enabled || !exclusive) {
            LOGGER.warn("wired.engine.enabled / wired.engine.exclusive are now compatibility-only flags. WiredManager runs as the exclusive engine runtime.");
        }

        LOGGER.info("Wired Manager initialized - enabled: {}, exclusive runtime active, maxSteps: {}, debug: {}",
                enabled, maxSteps, debug);
    }

    /**
     * Shutdown the wired manager.
     * Called during emulator shutdown.
     */
    public static synchronized void shutdown() {
        if (!initialized) {
            return;
        }

        LOGGER.info("Shutting down Wired Manager...");

        // Stop the tick service first
        WiredTickService.getInstance().stop();

        if (stackIndex != null) {
            stackIndex.clearAll();
        }

        if (engine != null) {
            engine.clearUnseenCache();
            engine.clearAllDiagnostics();
            engine.clearAllExecutionCaches();
        }

        initialized = false;
        LOGGER.info("Wired Manager shutdown complete");
    }

    /**
     * Check if the new wired engine is enabled.
     * @return true if enabled
     */
    public static boolean isEnabled() {
        return initialized && engine != null;
    }

    /**
     * Check if the new engine is exclusive (legacy disabled).
     * @return true if exclusive mode
     */
    public static boolean isExclusive() {
        return true;
    }

    /**
     * Get the wired engine instance.
     * @return the engine, or null if not initialized
     */
    public static WiredEngine getEngine() {
        return engine;
    }

    /**
     * Get the stack index instance.
     * @return the stack index, or null if not initialized
     */
    public static RoomWiredStackIndex getStackIndex() {
        return stackIndex;
    }

    /**
     * Get the current monitor snapshot for a room.
     * @param roomId the room ID
     * @return the diagnostics snapshot, or null if the engine is unavailable
     */
    public static WiredRoomDiagnostics.Snapshot getDiagnosticsSnapshot(int roomId) {
        if (engine == null) {
            return null;
        }

        return engine.getDiagnosticsSnapshot(roomId);
    }

    public static void clearDiagnosticsLogs(int roomId) {
        if (engine == null) {
            return;
        }

        engine.clearRoomDiagnosticsLogs(roomId);
    }

    // ========== Event Triggering Methods ==========

    /**
     * Handle a wired event using the new engine.
     * @param event the event to handle
     * @return true if any stack was triggered
     */
    public static boolean handleEvent(WiredEvent event) {
        return handleEvent(event, false);
    }

    public static boolean handleEvent(WiredEvent event, boolean negateConditions) {
        if (!isEnabled() || engine == null) {
            return false;
        }

        if (event == null || RoomWiredDisableSupport.isWiredDisabled(event.getRoom())) {
            return false;
        }

        Integer previousDepth = EVENT_HANDLING_DEPTH.get();
        int nextDepth = (previousDepth == null) ? 1 : (previousDepth + 1);
        EVENT_HANDLING_DEPTH.set(nextDepth);

        if (previousDepth == null) {
            DEFERRED_EFFECT_EVENTS.set(new ArrayDeque<>());
        }

        boolean handled = false;

        try {
            handled = engine.handleEvent(event, negateConditions);

            if (nextDepth == 1) {
                ArrayDeque<DeferredEffectEvent> deferredEvents = DEFERRED_EFFECT_EVENTS.get();

                while (deferredEvents != null && !deferredEvents.isEmpty()) {
                    DeferredEffectEvent deferredEvent = deferredEvents.pollFirst();

                    if (deferredEvent == null || deferredEvent.event == null || RoomWiredDisableSupport.isWiredDisabled(deferredEvent.event.getRoom())) {
                        continue;
                    }

                    handled = engine.handleEvent(deferredEvent.event, deferredEvent.negateConditions) || handled;
                }
            }

            return handled;
        } finally {
            if (previousDepth == null) {
                EVENT_HANDLING_DEPTH.remove();
                DEFERRED_EFFECT_EVENTS.remove();
            } else {
                EVENT_HANDLING_DEPTH.set(previousDepth);
            }
        }
    }

    public static boolean dispatchEffectTriggeredEvent(WiredEvent event) {
        return dispatchEffectTriggeredEvent(event, false);
    }

    public static boolean dispatchNegatedEffectTriggeredEvent(WiredEvent event) {
        return dispatchEffectTriggeredEvent(event, true);
    }

    private static boolean dispatchEffectTriggeredEvent(WiredEvent event, boolean negateConditions) {
        if (!isEnabled() || engine == null || event == null || RoomWiredDisableSupport.isWiredDisabled(event.getRoom())) {
            return false;
        }

        Integer currentDepth = EVENT_HANDLING_DEPTH.get();

        if (currentDepth == null || currentDepth <= 0) {
            return handleEvent(event, negateConditions);
        }

        ArrayDeque<DeferredEffectEvent> deferredEvents = DEFERRED_EFFECT_EVENTS.get();

        if (deferredEvents == null) {
            deferredEvents = new ArrayDeque<>();
            DEFERRED_EFFECT_EVENTS.set(deferredEvents);
        }

        deferredEvents.addLast(new DeferredEffectEvent(event, negateConditions));
        return true;
    }

    /**
     * Handle a wired event using the new engine when the source trigger item is already known.
     * Used by timed wired to avoid scanning unrelated stacks.
     */
    private static boolean handleEventForSourceItem(WiredEvent event, HabboItem sourceItem) {
        if (!isEnabled() || engine == null || event == null || sourceItem == null) {
            return false;
        }

        return engine.handleEventForSourceItem(event, sourceItem.getId());
    }

    /**
     * Trigger when a user walks onto furniture.
     */
    public static boolean triggerUserWalksOn(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userWalksOn(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user walks off furniture.
     */
    public static boolean triggerUserWalksOff(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userWalksOff(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user clicks furniture.
     */
    public static boolean triggerUserClicksFurni(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userClicksFurni(room, user, item);
        return handleEvent(event);
    }

    public static void queueUserClicksFurni(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return;
        }

        triggerUserClicksFurni(room, user, item);
    }

    public static void cancelPendingUserClicksFurni(Room room, RoomUnit user, HabboItem item) {
        // Click furni triggers are now executed immediately.
    }
    /**
     * Trigger when a user clicks invisible click tile furniture.
     */
    public static boolean triggerUserClicksTile(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || user == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userClicksTile(room, user, item);
        return handleEvent(event);
    }

    /**
     * Trigger when a user clicks another user.
     */
    public static boolean triggerUserClicksUser(Room room, RoomUnit clickingUser, RoomUnit clickedUser) {
        if (!isEnabled() || room == null || clickingUser == null || clickedUser == null) {
            return false;
        }

        WiredTriggerHabboClicksUser.clearRuntimeFlags(clickingUser);
        WiredEvent event = WiredEvents.userClicksUser(room, clickingUser, clickedUser);
        return handleEvent(event);
    }

    /**
     * Trigger when a user performs an avatar action.
     */
    public static boolean triggerUserPerformsAction(Room room, RoomUnit user, int actionId, int actionParameter) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        user.getCacheable().put(CACHE_LAST_ACTION_ID, actionId);
        user.getCacheable().put(CACHE_LAST_ACTION_PARAMETER, actionParameter);
        user.getCacheable().put(CACHE_LAST_ACTION_TIMESTAMP, System.currentTimeMillis());

        WiredEvent event = WiredEvents.userPerformsAction(room, user, actionId, actionParameter);
        return handleEvent(event);
    }

    /**
     * Trigger when a user says something.
     */
    public static boolean triggerUserSays(Room room, RoomUnit user, String message) {
        return triggerUserSays(room, user, message, -1, -1);
    }

    public static boolean triggerUserSays(Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userSays(room, user, message, chatType, chatStyle);
        return handleEvent(event);
    }

    public static boolean shouldSuppressUserSaysOutput(Room room, RoomUnit user, String message) {
        return shouldSuppressUserSaysOutput(room, user, message, -1, -1);
    }

    public static boolean shouldSuppressUserSaysOutput(Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        if (!isEnabled() || engine == null || room == null || user == null) {
            return false;
        }

        if (RoomWiredDisableSupport.isWiredDisabled(room)) {
            return false;
        }

        WiredEvent event = WiredEvents.userSays(room, user, message, chatType, chatStyle);
        return engine.shouldSuppressUserSaysOutput(event);
    }

    /**
     * Trigger when a user enters the room.
     */
    public static boolean triggerUserEntersRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userEntersRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a user leaves the room.
     */
    public static boolean triggerUserLeavesRoom(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userLeavesRoom(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when furniture state changes.
     */
    public static boolean triggerFurniStateChanged(Room room, RoomUnit user, HabboItem item) {
        if (!isEnabled() || room == null || item == null) {
            return false;
        }

        WiredEvent event = WiredEvents.furniStateChanged(room, user, item);
        return handleEvent(event);
    }

    public static boolean triggerUserVariableChanged(Room room, int userId, int definitionItemId, boolean created, boolean deleted, WiredEvent.VariableChangeKind changeKind) {
        if (!isEnabled() || room == null || definitionItemId <= 0) {
            return false;
        }

        Habbo habbo = room.getHabbo(userId);
        RoomUnit roomUnit = (habbo != null) ? habbo.getRoomUnit() : null;
        WiredEvent event = WiredEvents.userVariableChanged(room, roomUnit, definitionItemId, created, deleted, changeKind);
        return handleEvent(event);
    }

    public static boolean triggerFurniVariableChanged(Room room, int furniId, int definitionItemId, boolean created, boolean deleted, WiredEvent.VariableChangeKind changeKind) {
        if (!isEnabled() || room == null || furniId <= 0 || definitionItemId <= 0) {
            return false;
        }

        HabboItem item = room.getHabboItem(furniId);
        WiredEvent event = WiredEvents.furniVariableChanged(room, item, definitionItemId, created, deleted, changeKind);
        return handleEvent(event);
    }

    public static boolean triggerRoomVariableChanged(Room room, int definitionItemId, WiredEvent.VariableChangeKind changeKind) {
        if (!isEnabled() || room == null || definitionItemId <= 0) {
            return false;
        }

        WiredEvent event = WiredEvents.roomVariableChanged(room, definitionItemId, changeKind);
        return handleEvent(event);
    }

    /**
     * Trigger a timer tick.
     */
    public static boolean triggerTimerTick(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null || timerItem == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerTick(room, timerItem);
        return handleEventForSourceItem(event, timerItem);
    }

    /**
     * Trigger a periodic timer.
     */
    public static boolean triggerTimerRepeat(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null || timerItem == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeat(room, timerItem);
        return handleEventForSourceItem(event, timerItem);
    }

    public static boolean triggerClockCounter(Room room, HabboItem counterItem) {
        if (!isEnabled() || room == null || counterItem == null) {
            return false;
        }

        WiredEvent event = WiredEvents.clockCounter(room, counterItem);
        return handleEventForSourceItem(event, counterItem);
    }

    /**
     * Trigger a long periodic timer.
     */
    public static boolean triggerTimerRepeatLong(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null || timerItem == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatLong(room, timerItem);
        return handleEventForSourceItem(event, timerItem);
    }

    /**
     * Trigger a short periodic timer.
     */
    public static boolean triggerTimerRepeatShort(Room room, HabboItem timerItem) {
        if (!isEnabled() || room == null || timerItem == null) {
            return false;
        }

        WiredEvent event = WiredEvents.timerRepeatShort(room, timerItem);
        return handleEventForSourceItem(event, timerItem);
    }

    /**
     * Trigger game start.
     */
    public static boolean triggerGameStarts(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.gameStarts(room);
        return handleEvent(event);
    }

    /**
     * Trigger game end.
     */
    public static boolean triggerGameEnds(Room room) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.gameEnds(room);
        return handleEvent(event);
    }

    /**
     * Trigger bot collision.
     */
    public static boolean triggerBotCollision(Room room, RoomUnit botUnit) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }

        WiredEvent event = WiredEvents.botCollision(room, botUnit);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches furniture.
     */
    public static boolean triggerBotReachedFurni(Room room, RoomUnit botUnit, HabboItem item) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }

        WiredEvent event = WiredEvents.botReachedFurni(room, botUnit, item);
        return handleEvent(event);
    }

    /**
     * Trigger when bot reaches a habbo.
     */
    public static boolean triggerBotReachedHabbo(Room room, RoomUnit botUnit, RoomUnit targetUser) {
        if (!isEnabled() || room == null || botUnit == null) {
            return false;
        }

        WiredEvent event = WiredEvents.botReachedHabbo(room, botUnit, targetUser);
        return handleEvent(event);
    }

    /**
     * Trigger when score is achieved.
     * @param room the room
     * @param user the user who scored
     * @param score the current total score
     * @param scoreAdded the amount of score just added
     */
    public static boolean triggerScoreAchieved(Room room, RoomUnit user, int score, int scoreAdded) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.scoreAchieved(room, user, score, scoreAdded);
        return handleEvent(event);
    }

    /**
     * Trigger when user starts idling.
     */
    public static boolean triggerUserIdles(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userIdles(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user stops idling.
     */
    public static boolean triggerUserUnidles(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userUnidles(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user starts dancing.
     */
    public static boolean triggerUserStartsDancing(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userStartsDancing(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when user stops dancing.
     */
    public static boolean triggerUserStopsDancing(Room room, RoomUnit user) {
        if (!isEnabled() || room == null || user == null) {
            return false;
        }

        WiredEvent event = WiredEvents.userStopsDancing(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a team wins a game.
     */
    public static boolean triggerTeamWins(Room room, RoomUnit user) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.teamWins(room, user);
        return handleEvent(event);
    }

    /**
     * Trigger when a team loses a game.
     */
    public static boolean triggerTeamLoses(Room room, RoomUnit user) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.teamLoses(room, user);
        return handleEvent(event);
    }

    /**
     * Compatibility bridge for code paths that still describe themselves as
     * legacy-triggered. Execution still goes through the new engine only.
     */
    public static boolean triggerFromLegacy(WiredTriggerType triggerType, RoomUnit roomUnit, Room room, Object[] stuff) {
        if (!isEnabled() || room == null) {
            return false;
        }

        WiredEvent event = WiredEvents.fromLegacy(triggerType, room, roomUnit, stuff);
        return handleEvent(event);
    }

    // ========== Index Management ==========

    /**
     * Invalidate the wired index for a room.
     * Call this when wired items are added/removed/moved.
     */
    public static void invalidateRoom(Room room) {
        if (room == null) {
            return;
        }

        if (stackIndex != null) {
            stackIndex.invalidateAll(room);
        }

        if (engine != null) {
            engine.clearRoomExecutionCaches(room.getId());
        }

        if (debugEnabled) {
            LOGGER.info("[Wired] Cache invalidated for room {}", room.getId());
        }
    }

    /**
     * Invalidate the wired index for a specific tile.
     */
    public static void invalidateTile(Room room, RoomTile tile) {
        if (stackIndex != null && room != null && tile != null) {
            stackIndex.invalidate(room, tile);
        }

        if (engine != null && room != null) {
            engine.clearRoomSourceStackCache(room.getId());
        }
    }

    /**
     * Rebuild the wired index for a room.
     */
    public static void rebuildRoom(Room room) {
        if (room == null) {
            return;
        }

        if (engine != null) {
            engine.clearRoomExecutionCaches(room.getId());
        }

        if (stackIndex != null) {
            stackIndex.rebuild(room);
        }
    }

    // ========== Configuration Constants (moved from WiredHandler) ==========

    /** Maximum number of furniture items that can be selected in a single wired component */
    public static int MAXIMUM_FURNI_SELECTION = 5;

    /** Delay in milliseconds between teleport executions */
    public static int TELEPORT_DELAY = 500;

    // ========== Debug Mode ==========

    /** Debug mode - when enabled, logs detailed wired execution flow */
    private static boolean debugEnabled = false;

    /**
     * Enables or disables wired debug mode.
     * When enabled, detailed execution logs are written to help troubleshoot wired stacks.
     *
     * @param enabled true to enable debug logging, false to disable
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
        if (enabled) {
            LOGGER.info("Wired debug mode ENABLED");
        }
    }

    /**
     * Checks if wired debug mode is enabled.
     *
     * @return true if debug mode is active
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Logs a debug message if debug mode is enabled.
     *
     * @param message the message to log
     * @param args optional format arguments
     */
    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[WIRED DEBUG] " + message, args);
        }
    }

    // ========== JSON Utilities ==========

    private static GsonBuilder gsonBuilder = null;
    private static Gson cachedGson = null;

    public static GsonBuilder getGsonBuilder() {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        return gsonBuilder;
    }

    /**
     * Gets a cached Gson instance. This is more efficient than calling
     * getGsonBuilder().create() multiple times, as Gson instances are thread-safe
     * and can be reused.
     *
     * @return a cached Gson instance
     */
    public static Gson getGson() {
        if (cachedGson == null) {
            cachedGson = getGsonBuilder().create();
        }
        return cachedGson;
    }

    // ========== Tick Service Integration ==========

    /**
     * Registers a tickable wired item with the centralized tick service.
     * <p>
     * Call this when a time-based wired trigger is placed in a room or when
     * a room is loaded.
     * </p>
     *
     * @param room the room the item is in
     * @param tickable the tickable item (e.g., WiredTriggerRepeater)
     */
    public static void registerTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().register(room, tickable);
    }

    /**
     * Unregisters a tickable wired item from the tick service.
     * <p>
     * Call this when a time-based wired trigger is picked up or when
     * a room is unloaded.
     * </p>
     *
     * @param room the room the item was in
     * @param tickable the tickable item
     */
    public static void unregisterTickable(Room room, WiredTickable tickable) {
        WiredTickService.getInstance().unregister(room, tickable);
    }

    /**
     * Unregisters all tickables for a room.
     * <p>
     * Call this when a room is unloaded to clean up all tick registrations.
     * </p>
     *
     * @param room the room
     */
    public static void unregisterRoomTickables(Room room) {
        WiredTickService.getInstance().unregisterRoom(room);
        if (room != null) {
            room.getFurniVariableManager().clearTransientAssignments();
            room.getRoomVariableManager().clearTransientAssignments();
            invalidateRoom(room);
        }
    }

    /**
     * Gets the tick service instance.
     *
     * @return the WiredTickService
     */
    public static WiredTickService getTickService() {
        return WiredTickService.getInstance();
    }

    public static boolean isTriggerExecutionAllowed(Room room, HabboItem triggerItem, long timestamp) {
        WiredExtraExecutionLimit executionLimit = getExecutionLimitExtra(room, triggerItem);

        return executionLimit == null || executionLimit.canExecuteAt(timestamp);
    }

    public static WiredExtraExecutionLimit getExecutionLimitExtra(Room room, HabboItem triggerItem) {
        if (room == null || triggerItem == null || room.getRoomSpecialTypes() == null) {
            return null;
        }

        THashSet<InteractionWiredExtra> extras = room.getRoomSpecialTypes().getExtras(
                triggerItem.getX(),
                triggerItem.getY());

        if (extras == null || extras.isEmpty()) {
            return null;
        }

        for (InteractionWiredExtra extra : extras) {
            if (extra instanceof WiredExtraExecutionLimit) {
                return (WiredExtraExecutionLimit) extra;
            }
        }

        return null;
    }

    // ========== Timer Management ==========

    /**
     * Resets all wired timers in a room.
     * <p>
     * This uses the new tick service for managing timer resets.
     * </p>
     *
     * @param room the room
     */
    public static void resetTimers(Room room) {
        if (!room.isLoaded())
            return;

        // Use the centralized tick service for timer resets
        WiredTickService.getInstance().resetRoomTimers(room);

        room.setLastTimerReset(Emulator.getIntUnixTimestamp());
    }

    // ========== Effect Execution ==========

    /**
     * Execute all wired effects at the specified tiles.
     * @param tiles the tiles to execute effects at
     * @param roomUnit the triggering room unit (may be null)
     * @param room the room
     * @param callStackDepth current recursion depth for trigger stacks
     * @return true if any effects were executed
     */
    public static boolean executeEffectsAtTiles(THashSet<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final int callStackDepth) {
        if (tiles == null || tiles.isEmpty() || room == null || engine == null || stackIndex == null) {
            return false;
        }

        for (RoomTile tile : tiles) {
            if (room != null) {
                THashSet<HabboItem> items = room.getItemsAt(tile);

                long millis = room.getCycleTimestamp();
                for (final HabboItem item : items) {
                    if (item instanceof InteractionWiredEffect && !(item instanceof WiredEffectTriggerStacks)) {
                        InteractionWiredEffect effect = (InteractionWiredEffect) item;
                        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                                .actor(roomUnit)
                                .callStackDepth(callStackDepth)
                                .build();
                        WiredContext ctx = new WiredContext(event, effect, DefaultWiredServices.getInstance(), new WiredState(100));
                        effect.execute(ctx);
                        effect.setCooldown(millis);
                    }
                }
            }
        }

        return true;
    }

    public static boolean executeNegatedStacksAtTiles(THashSet<RoomTile> tiles, final RoomUnit roomUnit, final Room room, final int callStackDepth) {
        if (tiles == null || tiles.isEmpty() || room == null || engine == null || stackIndex == null) {
            return false;
        }

        boolean handled = false;
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .actor(roomUnit)
                .callStackDepth(callStackDepth)
                .build();

        for (RoomTile tile : tiles) {
            List<WiredStack> stacks = stackIndex.getStacksAtTile(room, tile);
            if (stacks.isEmpty()) {
                continue;
            }

            for (WiredStack stack : stacks) {
                handled = engine.executeDirectStack(stack, event, true) || handled;
            }
        }

        return handled;
    }

    public static boolean executeNegatedTargetStacks(Iterable<HabboItem> triggerItems, final RoomUnit roomUnit, final Room room, final int callStackDepth) {
        if (triggerItems == null || room == null || engine == null || stackIndex == null || room.getLayout() == null) {
            return false;
        }

        boolean handled = false;
        Set<Integer> seenTriggerIds = new HashSet<>();
        WiredEvent event = WiredEvent.builder(WiredEvent.Type.CUSTOM, room)
                .actor(roomUnit)
                .callStackDepth(callStackDepth)
                .build();

        for (HabboItem triggerItem : triggerItems) {
            if (triggerItem == null || !seenTriggerIds.add(triggerItem.getId())) {
                continue;
            }

            RoomTile tile = room.getLayout().getTile(triggerItem.getX(), triggerItem.getY());
            if (tile == null) {
                continue;
            }

            List<WiredStack> stacks = stackIndex.getStacksAtTile(room, tile);
            if (stacks.isEmpty()) {
                continue;
            }

            for (WiredStack stack : stacks) {
                HabboItem stackTriggerItem = stack.triggerItem();
                if (stackTriggerItem == null || stackTriggerItem.getId() != triggerItem.getId()) {
                    continue;
                }

                handled = engine.executeDirectStack(stack, event, true) || handled;
                break;
            }
        }

        return handled;
    }

    private static final class DeferredEffectEvent {
        private final WiredEvent event;
        private final boolean negateConditions;

        private DeferredEffectEvent(WiredEvent event, boolean negateConditions) {
            this.event = event;
            this.negateConditions = negateConditions;
        }
    }

    // ========== Reward System ==========

    /**
     * Asynchronously drops/deletes all rewards given by a specific wired item.
     * Used when a wired reward box is picked up or reset.
     *
     * @param wiredId The ID of the wired item whose rewards should be deleted
     */
    public static void dropRewards(int wiredId) {
        Emulator.getThreading().run(() -> {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM wired_rewards_given WHERE wired_item = ?")) {
                statement.setInt(1, wiredId);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        });
    }

    private static void persistReward(int wiredId, int habboId, int rewardId, int timestamp) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO wired_rewards_given (wired_item, user_id, reward_id, timestamp) VALUES (?, ?, ?, ?)")) {
            statement.setInt(1, wiredId);
            statement.setInt(2, habboId);
            statement.setInt(3, rewardId);
            statement.setInt(4, timestamp);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private static void completeReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward, int successCode) {
        if (wiredBox.getLimit() > 0) {
            wiredBox.incrementGiven();
        }

        persistReward(wiredBox.getId(), habbo.getHabboInfo().getId(), reward.id, Emulator.getIntUnixTimestamp());
        habbo.getClient().sendResponse(new WiredRewardAlertComposer(successCode));
    }

    private static boolean giveReward(Habbo habbo, WiredEffectGiveReward wiredBox, WiredGiveRewardItem reward) {
        if (reward.badge) {
            UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, "badge", reward.data);
            if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled()) {
                return false;
            }

            if (rewardReceived.value.isEmpty()) {
                return false;
            }

            if (habbo.getInventory().getBadgesComponent().hasBadge(rewardReceived.value)) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED));
                return false;
            }

            HabboBadge badge = new HabboBadge(0, rewardReceived.value, 0, habbo);
            Emulator.getThreading().run(badge);
            habbo.getInventory().getBadgesComponent().addBadge(badge);
            habbo.getClient().sendResponse(new AddUserBadgeComposer(badge));
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_BADGE);
            return true;
        }

        String[] data = reward.data.split("#");

        if (data.length != 2) {
            return false;
        }

        UserWiredRewardReceived rewardReceived = new UserWiredRewardReceived(habbo, wiredBox, data[0], data[1]);
        if (Emulator.getPluginManager().fireEvent(rewardReceived).isCancelled()) {
            return false;
        }

        String rewardType = rewardReceived.type == null ? "" : rewardReceived.type.trim();
        String rewardValue = rewardReceived.value == null ? "" : rewardReceived.value.trim();

        if (rewardValue.isEmpty()) {
            return false;
        }

        if (rewardType.equalsIgnoreCase("credits")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.giveCredits(amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.equalsIgnoreCase("diamonds") || rewardType.equalsIgnoreCase("diamond")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.givePoints(5, amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.equalsIgnoreCase("pixels")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.givePixels(amount);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.startsWith("points")) {
            Integer points = parsePositiveRewardInteger(rewardValue);
            if (points == null) return false;

            int type = 5;

            try {
                int parsedType = Integer.parseInt(rewardType.replace("points", "").trim());
                if (parsedType > 0) {
                    type = parsedType;
                }
            } catch (NumberFormatException ignored) {
            }

            habbo.givePoints(type, points);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.equalsIgnoreCase("furni")) {
            Integer itemId = parsePositiveRewardInteger(rewardValue);
            if (itemId == null) return false;

            Item baseItem = Emulator.getGameEnvironment().getItemManager().getItem(itemId);
            if (baseItem == null) {
                return false;
            }

            HabboItem item = Emulator.getGameEnvironment().getItemManager().createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, "");
            if (item == null) {
                return false;
            }

            habbo.getClient().sendResponse(new AddHabboItemComposer(item));
            habbo.getClient().getHabbo().getInventory().getItemsComponent().addItem(item);
            habbo.getClient().sendResponse(new PurchaseOKComposer(null));
            habbo.getClient().sendResponse(new InventoryRefreshComposer());
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.equalsIgnoreCase("respect")) {
            Integer amount = parsePositiveRewardInteger(rewardValue);
            if (amount == null) return false;

            habbo.getHabboStats().respectPointsReceived += amount;
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        if (rewardType.equalsIgnoreCase("cata")) {
            Integer catalogItemId = parsePositiveRewardInteger(rewardValue);
            if (catalogItemId == null) return false;

            CatalogItem item = Emulator.getGameEnvironment().getCatalogManager().getCatalogItem(catalogItemId);
            if (item == null) {
                return false;
            }

            Emulator.getGameEnvironment().getCatalogManager().purchaseItem(null, item, habbo, 1, "", true);
            completeReward(habbo, wiredBox, reward, WiredRewardAlertComposer.REWARD_RECEIVED_ITEM);
            return true;
        }

        return false;
    }

    private static Integer parsePositiveRewardInteger(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static boolean getReward(Habbo habbo, WiredEffectGiveReward wiredBox) {
        synchronized (wiredBox) {
            if (wiredBox.getLimit() > 0) {
                if (wiredBox.getLimit() - wiredBox.getGiven() == 0) {
                    habbo.getClient().sendResponse(new WiredRewardAlertComposer(WiredRewardAlertComposer.LIMITED_NO_MORE_AVAILABLE));
                    return false;
                }
            }

            WiredGiveRewardItem rewardToGive = null;
            int failureCode = -1;

            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM wired_rewards_given WHERE user_id = ? AND wired_item = ? ORDER BY timestamp DESC LIMIT ?", ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY)) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                statement.setInt(2, wiredBox.getId());
                statement.setInt(3, wiredBox.getRewardItems().size());

                try (ResultSet set = statement.executeQuery()) {
                    if (set.first()) {
                        set.last();
                        int rowCount = set.getRow();
                        set.first();

                        if (rowCount >= 1) {
                            if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_ONCE) {
                                failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED;
                            }
                        }

                        if (failureCode == -1) {
                            if (wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_MINUTES) {
                                if (Emulator.getIntUnixTimestamp() - set.getInt("timestamp") <= 60) {
                                    failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_MINUTE;
                                }
                            }

                            if (failureCode == -1 && wiredBox.isUniqueRewards()) {
                                if (rowCount == wiredBox.getRewardItems().size()) {
                                    failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                                }
                            }

                            if (failureCode == -1 && wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_HOURS) {
                                if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (3600 * wiredBox.getLimitationInterval()))) {
                                    failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_HOUR;
                                }
                            }

                            if (failureCode == -1 && wiredBox.getRewardTime() == WiredEffectGiveReward.LIMIT_N_DAY) {
                                if (!(Emulator.getIntUnixTimestamp() - set.getInt("timestamp") >= (86400 * wiredBox.getLimitationInterval()))) {
                                    failureCode = WiredRewardAlertComposer.REWARD_ALREADY_RECEIVED_THIS_TODAY;
                                }
                            }
                        }

                        if (failureCode == -1) {
                            if (wiredBox.isUniqueRewards()) {
                                for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                                    set.beforeFirst();
                                    boolean found = false;

                                    while (set.next()) {
                                        if (set.getInt("reward_id") == item.id)
                                            found = true;
                                    }

                                    if (!found) {
                                        rewardToGive = item;
                                        break;
                                    }
                                }

                                if (rewardToGive == null) {
                                    failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
                return false;
            }

            if (failureCode != -1) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(failureCode));
                return false;
            }

            if (rewardToGive == null) {
                if (wiredBox.isUniqueRewards()) {
                    if (!wiredBox.getRewardItems().isEmpty()) {
                        rewardToGive = wiredBox.getRewardItems().get(0);
                    } else {
                        failureCode = WiredRewardAlertComposer.REWARD_ALL_COLLECTED;
                    }
                } else {
                    int randomNumber = Emulator.getRandom().nextInt(101);
                    int count = 0;
                    for (WiredGiveRewardItem item : wiredBox.getRewardItems()) {
                        if (randomNumber >= count && randomNumber <= (count + item.probability)) {
                            rewardToGive = item;
                            break;
                        }
                        count += item.probability;
                    }

                    if (rewardToGive == null) {
                        failureCode = WiredRewardAlertComposer.UNLUCKY_NO_REWARD;
                    }
                }
            }

            if (failureCode != -1) {
                habbo.getClient().sendResponse(new WiredRewardAlertComposer(failureCode));
                return false;
            }

            if (rewardToGive != null) {
                return giveReward(habbo, wiredBox, rewardToGive);
            }

            return false;
        }
    }
}
