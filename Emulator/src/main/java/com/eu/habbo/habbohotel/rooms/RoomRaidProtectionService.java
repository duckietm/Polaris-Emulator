package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionMonitor;
import com.eu.habbo.habbohotel.rooms.raidprotection.RaidProtectionSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import java.sql.SQLException;
import java.util.Set;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Raid protection for one room: the stored settings, the live score, and the action taken when the
 * score says a raid is under way.
 *
 * <p>Shaped after {@link RoomWiredAccessService}: settings load lazily on first use, a save updates
 * memory immediately and persists off the room thread, and a failed write rolls the memory back.
 */
final class RoomRaidProtectionService {
    /** The save succeeded; the client closes its window on this code. */
    static final int RESULT_OK = 0;

    /** The user may not manage this room's protection. */
    static final int RESULT_NOT_AUTHORIZED = 1;

    /** At least one value was outside the set the client offers. */
    static final int RESULT_INVALID = 2;

    private static final Logger LOGGER = LoggerFactory.getLogger(RoomRaidProtectionService.class);

    private final Room room;
    private final RoomRepository repository;
    private final IntSupplier unixTime;
    private final RaidProtectionMonitor monitor = new RaidProtectionMonitor();
    private final Object lock = new Object();

    private volatile boolean loaded;
    private RaidProtectionSettings settings;
    private int lastRaidAtSeconds;
    private volatile boolean incidentActive;

    /** Epoch second the raised guard sensitivity stops applying; 0 when no guard is running. */
    private volatile long guardUntilSeconds;

    RoomRaidProtectionService(Room room, RoomRepository repository, IntSupplier unixTime) {
        this.room = room;
        this.repository = repository;
        this.unixTime = unixTime;
    }

    RaidProtectionSettings settings() {
        this.ensureLoaded();
        return this.settings;
    }

    int lastRaidAtSeconds() {
        this.ensureLoaded();
        return this.lastRaidAtSeconds;
    }

    boolean incidentActive() {
        return this.incidentActive;
    }

    /** The room's owner, plus hotel staff who can already kick and ban from the moderation tool. */
    boolean canManage(Habbo habbo) {
        if (habbo == null) {
            return false;
        }

        return this.room.isOwner(habbo) || habbo.hasPermission(Permission.ACC_SUPPORTTOOL);
    }

    int save(Habbo habbo, RaidProtectionSettings requested) {
        if (!this.canManage(habbo)) {
            return RESULT_NOT_AUTHORIZED;
        }

        // The client refuses out-of-range values before sending; a modified one would not.
        if (requested == null || !requested.isValid()) {
            return RESULT_INVALID;
        }

        this.ensureLoaded();

        synchronized (this.lock) {
            RaidProtectionSettings previous = this.settings;
            this.settings = requested;

            if (!requested.isEnabled()) {
                this.monitor.reset();
                this.incidentActive = false;
                this.guardUntilSeconds = 0L;
            }

            this.room.threading().run(() -> this.persist(requested, previous));
        }

        return RESULT_OK;
    }

    /**
     * Scores a user walking in. Someone with rights in the room is never part of a raid, so their
     * arrival is not counted at all.
     */
    void onArrival(Habbo habbo) {
        if (habbo == null || !this.isWatching() || this.room.hasRights(habbo)) {
            return;
        }

        this.monitor.recordArrival(habbo.getHabboInfo().getId(), this.now());
        this.evaluate();
    }

    /** Scores a chat message. Only a recent arrival's message counts; see the monitor. */
    void onTalk(Habbo habbo, String message) {
        if (habbo == null || !this.isWatching() || this.room.hasRights(habbo)) {
            return;
        }

        this.monitor.recordMessage(habbo.getHabboInfo().getId(), message, this.now());
        this.evaluate();
    }

    /** Drops the live score, for a room that is unloading. */
    void reset() {
        this.monitor.reset();
        this.incidentActive = false;
        this.guardUntilSeconds = 0L;
    }

    private boolean isWatching() {
        return this.settings().isEnabled();
    }

    private void evaluate() {
        long now = this.now();
        Set<Integer> contributors = this.monitor.evaluate(this.effectiveSensitivity(now), now);

        if (contributors == null || contributors.isEmpty()) {
            return;
        }

        RaidProtectionSettings current = this.settings();
        this.incidentActive = true;
        this.lastRaidAtSeconds = (int) now;

        if (current.isGuardEnabled()) {
            this.guardUntilSeconds = now + current.getGuardDurationSeconds();
        }

        this.room.threading().run(() -> this.recordRaid(this.room.getId(), (int) now));

        for (int userId : contributors) {
            this.act(current, userId);
        }
    }

    /** While the guard is running after an incident, the room watches at the guard's sensitivity. */
    private int effectiveSensitivity(long now) {
        RaidProtectionSettings current = this.settings();

        if (current.isGuardEnabled() && now < this.guardUntilSeconds) {
            return current.getGuardSensitivity();
        }

        return current.getDetectionSensitivity();
    }

    private void act(RaidProtectionSettings current, int userId) {
        Habbo habbo = this.room.getHabbo(userId);

        if (current.getActionType() == RaidProtectionSettings.ACTION_TEMPORARY_BAN) {
            this.room
                    .gameEnvironment()
                    .getRoomManager()
                    .banUserFromRoom(null, userId, this.room.getId(), current.getBanDurationSeconds());
            return;
        }

        if (habbo != null) {
            this.room.kickHabbo(habbo, true);
        }
    }

    private long now() {
        return this.unixTime.getAsInt();
    }

    private void ensureLoaded() {
        if (this.loaded) {
            return;
        }

        synchronized (this.lock) {
            if (this.loaded) {
                return;
            }

            this.settings = RaidProtectionSettings.defaults(this.room.getId());
            this.lastRaidAtSeconds = 0;

            try {
                RoomRepository.RaidProtection stored = this.repository.findRaidProtection(this.room.getId());

                if (stored.settings().isValid()) {
                    this.settings = stored.settings();
                }

                this.lastRaidAtSeconds = stored.lastRaidAtSeconds();
            } catch (SQLException exception) {
                LOGGER.error("Caught SQL exception while loading raid protection settings", exception);
            }

            this.loaded = true;
        }
    }

    private void persist(RaidProtectionSettings saved, RaidProtectionSettings previous) {
        try {
            this.repository.saveRaidProtection(saved);
        } catch (SQLException exception) {
            synchronized (this.lock) {
                if (this.settings == saved) {
                    this.settings = previous;
                }
            }

            LOGGER.error("Caught SQL exception while saving raid protection settings", exception);
        }
    }

    private void recordRaid(int roomId, int atSeconds) {
        try {
            this.repository.recordRaid(roomId, atSeconds);
        } catch (SQLException exception) {
            LOGGER.error("Caught SQL exception while recording a raid", exception);
        }
    }
}
