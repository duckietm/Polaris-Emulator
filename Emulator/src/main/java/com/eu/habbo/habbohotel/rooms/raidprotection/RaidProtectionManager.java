package com.eu.habbo.habbohotel.rooms.raidprotection;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.database.Database;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomBan;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorCode;
import com.eu.habbo.messages.outgoing.generic.alerts.GenericErrorMessagesComposer;
import com.eu.habbo.messages.outgoing.hotelview.HotelViewComposer;
import com.eu.habbo.messages.outgoing.rooms.RoomEnterErrorComposer;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionCapabilityComposer;
import com.eu.habbo.messages.outgoing.rooms.raidprotection.RaidProtectionSettingsComposer;
import com.eu.habbo.threading.ThreadPooling;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raid protection of rooms (AIR navigator/raidprotection). The room's owner (or staff with
 * {@code acc_anyroomowner}) sets it up from the room info window; the server watches who enters and
 * who speaks, detects a raid from the settings' sensitivity, and then kicks or temporarily bans the
 * raiders, keeps suspicious visitors out while the guard is on, records the incident and tells the
 * owner. With protection off a raid is still detected and reported to the owner as a "shadow" alert,
 * so they know to switch it on.
 *
 * <p>Settings are read once per room (the first entry after a restart) and written through on save;
 * detection itself is in memory ({@link RaidDetector}). The database sees one row per save and two
 * statements per raid, run off the packet thread.
 */
public final class RaidProtectionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(RaidProtectionManager.class);

    public static final String ENABLED_KEY = "hotel.raid.protection.enabled";
    public static final String NEW_ACCOUNT_DAYS_KEY = "hotel.raid.protection.new_account_days";
    public static final String QUIET_SECONDS_KEY = "hotel.raid.protection.quiet_seconds";

    /** AIR package_230/class_4240 result codes; texts raid.protection.settings.save.fail.N. */
    public static final int RESULT_SAVED = 0;

    public static final int RESULT_FEATURE_DISABLED = 1;
    public static final int RESULT_ROOM_UNAVAILABLE = 2;
    public static final int RESULT_NO_RIGHTS = 3;
    public static final int RESULT_INVALID = 4;
    public static final int RESULT_CONFIRMATION_REQUIRED = 5;
    public static final int RESULT_SAVE_FAILED = 6;

    /** Notification types: the client reads notification.&lt;type&gt;.title / .message. */
    static final String ALERT_ACTIVE = "raid.protection.active_alert";

    static final String ALERT_SHADOW = "raid.protection.shadow_alert";
    static final String SHADOW_LINK_TITLE = "raid.protection.shadow_alert.link";

    private static final RaidProtectionManager INSTANCE = new RaidProtectionManager(
            new RaidProtectionRepository(() -> {
                Database database = WiredPlatform.database();
                if (database == null) throw new SQLException("The database is not available");
                return database.getDataSource().getConnection();
            }),
            task -> {
                ThreadPooling threading = WiredPlatform.threading();
                if (threading != null) threading.run(task);
            });

    private final RaidProtectionRepository repository;
    private final Executor executor;
    private final Map<Integer, RoomState> rooms = new ConcurrentHashMap<>();

    static final class RoomState {
        volatile RaidProtectionSettings settings;
        volatile int lastRaidAt;
        final RaidDetector detector = new RaidDetector();
        /** The id of the incident going on, once its row is written. */
        volatile CompletableFuture<Integer> incident;

        RoomState(RaidProtectionSettings settings, int lastRaidAt) {
            this.settings = settings;
            this.lastRaidAt = lastRaidAt;
        }
    }

    RaidProtectionManager(RaidProtectionRepository repository, Executor executor) {
        this.repository = repository;
        this.executor = executor;
    }

    public static RaidProtectionManager get() {
        return INSTANCE;
    }

    public static boolean isFeatureEnabled() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        return configuration != null && configuration.getBoolean(ENABLED_KEY, true);
    }

    static long quietMs() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        int seconds = configuration == null ? 120 : configuration.getInt(QUIET_SECONDS_KEY, 120);
        return Math.max(10, seconds) * 1000L;
    }

    static int newAccountDays() {
        ConfigurationManager configuration = WiredPlatform.configuration();
        return configuration == null ? 7 : configuration.getInt(NEW_ACCOUNT_DAYS_KEY, 7);
    }

    /** AIR's canManage: the owner, or staff that may manage any room. */
    public static boolean canManage(Room room, Habbo habbo) {
        return room != null && habbo != null && room.isOwner(habbo);
    }

    /**
     * The answer to a save, in AIR's order of checks. {@code enabling} is a save that switches
     * protection on; AIR asks the owner first and sends {@code confirmed}.
     */
    public static int saveResult(
            boolean featureEnabled,
            boolean roomAvailable,
            boolean canManage,
            boolean valid,
            boolean enabling,
            boolean confirmed) {
        if (!featureEnabled) return RESULT_FEATURE_DISABLED;
        if (!roomAvailable) return RESULT_ROOM_UNAVAILABLE;
        if (!canManage) return RESULT_NO_RIGHTS;
        if (!valid) return RESULT_INVALID;
        if (enabling && !confirmed) return RESULT_CONFIRMATION_REQUIRED;
        return RESULT_SAVED;
    }

    // ---- packets ------------------------------------------------------------------------------

    /** On room entry: tells the visitor whether the room info shows the raid protection button. */
    public void sendCapability(Room room, Habbo habbo) {
        if (room == null || habbo == null || habbo.getClient() == null || !isFeatureEnabled()) return;
        habbo.getClient().sendResponse(new RaidProtectionCapabilityComposer(room.getId(), canManage(room, habbo)));
    }

    public RaidProtectionSnapshot snapshot(Room room) {
        RoomState state = this.state(room.getId());
        long now = System.currentTimeMillis();
        this.closeIfQuiet(state, now);
        return new RaidProtectionSnapshot(
                state.settings, state.detector.isIncidentActive(now, quietMs()), state.lastRaidAt);
    }

    /**
     * Stores the settings the owner saved; returns the result code. The caller answers with the
     * snapshot in force afterwards.
     */
    public int save(Room room, Habbo habbo, RaidProtectionSettings requested, boolean confirmed) {
        boolean available =
                room != null && room.isLoaded() && habbo.getHabboInfo().getCurrentRoom() == room;
        RoomState state = available ? this.state(room.getId()) : null;
        boolean enabling = state != null && !state.settings.enabled() && requested.enabled();
        int result = saveResult(
                isFeatureEnabled(),
                available,
                available && canManage(room, habbo),
                requested.isValid(),
                enabling,
                confirmed);
        if (result != RESULT_SAVED) return result;

        try {
            this.repository.save(requested, WiredPlatform.unixTimestamp());
        } catch (SQLException exception) {
            LOGGER.error("Could not save the raid protection of room {}", room.getId(), exception);
            return RESULT_SAVE_FAILED;
        }
        state.settings = requested;
        return RESULT_SAVED;
    }

    // ---- detection ----------------------------------------------------------------------------

    /**
     * At the door, after the ban check: whether the visitor may come in. A refused visitor has been
     * sent the reason (kicked, banned or kept out by the guard).
     */
    public boolean admit(Room room, Habbo habbo, boolean firstVisit) {
        if (room == null || habbo == null || !isFeatureEnabled()) return true;

        int level = this.suspicion(room, habbo, firstVisit);
        if (level == RaidSuspicion.TRUSTED) return true;

        int userId = habbo.getHabboInfo().getId();
        RoomState state = this.state(room.getId());
        long now = System.currentTimeMillis();
        long quietMs = quietMs();
        this.closeIfQuiet(state, now);
        RaidProtectionSettings settings = state.settings;

        if (state.detector.isBlocked(userId, now)) {
            state.detector.recordBlocked();
            refuseAtDoor(habbo);
            return false;
        }

        boolean active = state.detector.isIncidentActive(now, quietMs);
        if (settings.enabled()
                && settings.guardEnabled()
                && active
                && RaidSuspicion.guardBlocks(level, settings.guardSensitivity())) {
            state.detector.block(userId, now, now + settings.guardDurationSeconds() * 1000L);
            state.detector.recordBlocked();
            refuseAtDoor(habbo);
            return false;
        }

        if (!RaidSuspicion.counts(level) && !(active && state.detector.isParticipant(userId))) return true;

        RaidDetector.Result result = state.detector.onEntry(userId, settings.sensitivity(), now, quietMs);
        return !this.handle(room, state, result, habbo, userId, now);
    }

    /** A chat line; returns true when its speaker was just acted on and the line is to be dropped. */
    public boolean onChat(Room room, Habbo habbo) {
        if (room == null || habbo == null || !isFeatureEnabled()) return false;
        RoomState state = this.rooms.get(room.getId());
        if (state == null) return false;

        long now = System.currentTimeMillis();
        this.closeIfQuiet(state, now);
        RaidDetector.Result result =
                state.detector.onChat(habbo.getHabboInfo().getId(), state.settings.sensitivity(), now, quietMs());
        if (result.kind() == RaidDetector.Kind.NONE) return false;
        if (this.suspicion(room, habbo, false) == RaidSuspicion.TRUSTED) return false;
        return this.handle(room, state, result, null, habbo.getHabboInfo().getId(), now);
    }

    /** The room unloads: the incident going on ends now and the room's state is dropped. */
    public void onRoomUnloaded(int roomId) {
        RoomState state = this.rooms.remove(roomId);
        if (state == null) return;
        RaidDetector.Summary summary = state.detector.closeNow(System.currentTimeMillis());
        if (summary != null) this.finishIncident(state, summary);
    }

    /**
     * Acts on a detection. {@code entrant} is a visitor still at the door: refused instead of removed.
     *
     * @return true when {@code subjectId} (the entrant, or the speaker of a chat line) was acted on
     */
    private boolean handle(
            Room room, RoomState state, RaidDetector.Result result, Habbo entrant, int subjectId, long now) {
        if (result.kind() == RaidDetector.Kind.NONE) return false;
        RaidProtectionSettings settings = state.settings;

        if (result.kind() == RaidDetector.Kind.STARTED) {
            this.startIncident(room, state, settings, result.trigger(), now);
        }
        if (!settings.enabled()) return false;

        boolean subjectActedOn = false;
        for (int userId : result.targets()) {
            if (entrant != null && entrant.getHabboInfo().getId() == userId) {
                this.actOnEntrant(room, state, settings, entrant, now);
                subjectActedOn |= userId == subjectId;
                continue;
            }
            Habbo habbo = room.getHabbo(userId);
            if (habbo == null || this.suspicion(room, habbo, true) == RaidSuspicion.TRUSTED) continue;
            if (this.actInRoom(room, state, settings, habbo, now)) subjectActedOn |= userId == subjectId;
        }
        return subjectActedOn;
    }

    private void actOnEntrant(Room room, RoomState state, RaidProtectionSettings settings, Habbo habbo, long now) {
        state.detector.recordAction();
        this.guard(state, settings, habbo, now);
        if (settings.actionType() == RaidProtectionSettings.ACTION_TEMPORARY_BAN) {
            this.ban(room, settings, habbo);
            habbo.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
            return;
        }
        habbo.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.KICKED_OUT_OF_ROOM));
        habbo.getClient().sendResponse(new HotelViewComposer());
    }

    private boolean actInRoom(Room room, RoomState state, RaidProtectionSettings settings, Habbo habbo, long now) {
        if (habbo.getHabboInfo().getCurrentRoom() != room) return false;
        state.detector.recordAction();
        this.guard(state, settings, habbo, now);
        if (settings.actionType() == RaidProtectionSettings.ACTION_TEMPORARY_BAN) {
            this.ban(room, settings, habbo);
            leaveRoom(habbo, room);
            habbo.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_BANNED));
            return true;
        }
        habbo.getClient().sendResponse(new GenericErrorMessagesComposer(GenericErrorCode.KICKED_OUT_OF_ROOM));
        leaveRoom(habbo, room);
        return true;
    }

    /** A raider of a guarded room stays out for the guard duration, at the guard's sensitivity. */
    private void guard(RoomState state, RaidProtectionSettings settings, Habbo habbo, long now) {
        if (!settings.guardEnabled()) return;
        int level = this.suspicion(null, habbo, true);
        if (!RaidSuspicion.guardBlocks(level, settings.guardSensitivity())) return;
        state.detector.block(habbo.getHabboInfo().getId(), now, now + settings.guardDurationSeconds() * 1000L);
    }

    private void ban(Room room, RaidProtectionSettings settings, Habbo habbo) {
        RoomBan ban = new RoomBan(
                room.getId(),
                habbo.getHabboInfo().getId(),
                habbo.getHabboInfo().getUsername(),
                WiredPlatform.unixTimestamp() + settings.banDurationSeconds());
        room.addRoomBan(ban);
        this.executor.execute(ban::insert);
    }

    private static void leaveRoom(Habbo habbo, Room room) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment != null) environment.getRoomManager().leaveRoom(habbo, room);
    }

    private static void refuseAtDoor(Habbo habbo) {
        habbo.getClient().sendResponse(new RoomEnterErrorComposer(RoomEnterErrorComposer.ROOM_ERROR_CANT_ENTER));
    }

    /**
     * The visitor's suspicion level. {@code room} null skips the room checks (rights, friendship
     * with the owner), for a visitor already found suspicious.
     */
    private int suspicion(Room room, Habbo habbo, boolean firstVisit) {
        boolean trusted = habbo.hasPermission(Permission.ACC_UNKICKABLE);
        if (!trusted && room != null) {
            trusted = room.hasRights(habbo)
                    || (habbo.getMessenger() != null && habbo.getMessenger().getFriend(room.getOwnerId()) != null);
        }
        long age = (long) WiredPlatform.unixTimestamp() - habbo.getHabboInfo().getAccountCreated();
        return RaidSuspicion.level(trusted, firstVisit, age, newAccountDays());
    }

    // ---- incidents ----------------------------------------------------------------------------

    private void startIncident(Room room, RoomState state, RaidProtectionSettings settings, String trigger, long now) {
        int startedAt = (int) (now / 1000L);
        state.lastRaidAt = startedAt;
        CompletableFuture<Integer> incident = new CompletableFuture<>();
        state.incident = incident;
        this.executor.execute(() -> {
            try {
                incident.complete(this.repository.startIncident(settings, trigger, startedAt));
            } catch (SQLException | RuntimeException exception) {
                LOGGER.error("Could not record the raid in room {}", settings.roomId(), exception);
                incident.complete(0);
            }
        });
        LOGGER.info(
                "Raid {} in room {} ({}): protection {}",
                trigger,
                room.getId(),
                room.getName(),
                settings.enabled() ? "applied" : "off, owner alerted");
        this.alertOwner(room, settings, state, now);
    }

    private void closeIfQuiet(RoomState state, long now) {
        RaidDetector.Summary summary = state.detector.closeIfQuiet(now, quietMs());
        if (summary != null) this.finishIncident(state, summary);
    }

    private void finishIncident(RoomState state, RaidDetector.Summary summary) {
        CompletableFuture<Integer> incident = state.incident;
        state.incident = null;
        if (incident == null) return;
        incident.thenAccept(id -> {
            if (id <= 0) return;
            this.executor.execute(() -> {
                try {
                    this.repository.finishIncident(id, summary);
                } catch (SQLException | RuntimeException exception) {
                    LOGGER.error("Could not close raid incident {}", id, exception);
                }
            });
        });
    }

    /** AIR's active alert, or the shadow alert with its link to the settings when protection is off. */
    private void alertOwner(Room room, RaidProtectionSettings settings, RoomState state, long now) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        Habbo owner = environment == null ? null : environment.getHabboManager().getHabbo(room.getOwnerId());
        if (owner == null || owner.getClient() == null) return;

        Map<String, String> keys = new HashMap<>();
        keys.put("roomname", room.getName());
        if (!settings.enabled()) {
            keys.put("linkUrl", "navigator/raidprotection/" + room.getId());
            keys.put("linkTitle", SHADOW_LINK_TITLE);
        }
        owner.getClient().sendResponse(new BubbleAlertComposer(settings.enabled() ? ALERT_ACTIVE : ALERT_SHADOW, keys));

        // An owner in the room with the window open sees the incident status change at once.
        if (owner.getHabboInfo().getCurrentRoom() == room) {
            owner.getClient()
                    .sendResponse(new RaidProtectionSettingsComposer(new RaidProtectionSnapshot(
                            settings, state.detector.isIncidentActive(now, quietMs()), state.lastRaidAt)));
        }
    }

    private RoomState state(int roomId) {
        RoomState state = this.rooms.get(roomId);
        if (state != null) return state;

        RaidProtectionRepository.Stored stored;
        try {
            stored = this.repository.load(roomId);
        } catch (SQLException | RuntimeException exception) {
            LOGGER.error("Could not load the raid protection of room {}", roomId, exception);
            stored = new RaidProtectionRepository.Stored(RaidProtectionSettings.defaults(roomId), 0);
        }
        RoomState loaded = new RoomState(stored.settings(), stored.lastRaidAt());
        RoomState previous = this.rooms.putIfAbsent(roomId, loaded);
        return previous != null ? previous : loaded;
    }
}
