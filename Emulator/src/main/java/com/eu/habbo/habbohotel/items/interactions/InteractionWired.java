package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredInputGuard;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.WiredBoxFlashes;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base abstract class for all wired furniture items (triggers, effects, conditions, extras).
 * <p>
 * The wired system allows room owners to create automated behaviors in their rooms.
 * It consists of:
 * <ul>
 *   <li><b>Triggers</b> ({@link InteractionWiredTrigger}) - Events that start the wired chain</li>
 *   <li><b>Conditions</b> ({@link InteractionWiredCondition}) - Requirements that must be met</li>
 *   <li><b>Effects</b> ({@link InteractionWiredEffect}) - Actions that are executed</li>
 *   <li><b>Extras</b> ({@link InteractionWiredExtra}) - Modifiers like random selection</li>
 * </ul>
 * </p>
 * <p>
 * Wired items at the same tile coordinates form a "stack" and execute together.
 * The {@link com.eu.habbo.habbohotel.wired.core.WiredManager} orchestrates execution.
 * </p>
 * <p>
 * Key features:
 * <ul>
 *   <li>Cooldown system to prevent spam triggering</li>
 *   <li>Per-user execution caching with automatic cleanup</li>
 *   <li>JSON-based data persistence via {@link #getWiredData()}</li>
 * </ul>
 * </p>
 *
 * @see com.eu.habbo.habbohotel.wired.core.WiredManager
 * @see com.eu.habbo.habbohotel.rooms.RoomSpecialTypes
 */
public abstract class InteractionWired extends InteractionDefault {
    private static final Logger LOGGER = LoggerFactory.getLogger(InteractionWired.class);

    /**
     * Maximum number of entries in the user execution cache to prevent memory leaks.
     */
    private static final int MAX_USER_CACHE_SIZE = 500;

    /**
     * Cache entries older than this (in milliseconds) will be cleaned up.
     * Default: 5 minutes
     */
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000;

    private volatile long cooldown;
    // Ensures one box is processed by a single thread at a time, so the
    // cooldown check-and-set in WiredHandler can't double-fire when a packet
    // thread and the room cycle thread trigger the same box concurrently.
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final ConcurrentHashMap<Long, Long> userExecutionCache = new ConcurrentHashMap<>();

    /** The box was never saved since it was placed: its editor opens on the official default sources. */
    private volatile boolean editorFresh;

    InteractionWired(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
        this.editorFresh = storedWiredDataIsEmpty(set);
    }

    InteractionWired(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
        this.editorFresh = true;
    }

    private static boolean storedWiredDataIsEmpty(ResultSet set) {
        try {
            String stored = set.getString("wired_data");
            return stored == null || stored.isBlank();
        } catch (SQLException e) {
            return false;
        }
    }

    /** Whether the box was never saved since it was placed. */
    public boolean isEditorFresh() {
        return this.editorFresh;
    }

    /** A save went through: the editor now opens on what the box holds. */
    public void markEditorSaved() {
        this.editorFresh = false;
    }

    /**
     * Executes this wired item's logic.
     *
     * @param roomUnit the room unit that triggered this (may be null for non-user triggers)
     * @param room the room where this is happening
     * @param stuff additional context data passed from the trigger
     * @return true if execution was successful, false otherwise
     */
    public abstract boolean execute(RoomUnit roomUnit, Room room, Object[] stuff);

    public abstract String getWiredData();

    public abstract void serializeWiredData(ServerMessage message, Room room);

    public abstract void loadWiredData(ResultSet set, Room room) throws SQLException;

    @Override
    public void run() {
        if (this.needsUpdate()) {
            String wiredDataRaw = this.getWiredData();
            final String wiredData = (wiredDataRaw == null) ? "" : wiredDataRaw;
            final int currentRoomId = this.getRoomId();
            final int currentId = this.getId();

            Emulator.getThreading().run(() -> {
                try (Connection connection =
                                Emulator.getDatabase().getDataSource().getConnection();
                        PreparedStatement statement =
                                connection.prepareStatement("UPDATE items SET wired_data = ? WHERE id = ?")) {
                    if (currentRoomId != 0) {
                        statement.setString(1, wiredData);
                    } else {
                        statement.setString(1, "");
                    }
                    statement.setInt(2, currentId);
                    statement.execute();
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            });
        }
        super.run();
    }

    @Override
    public void onPickUp(Room room) {
        this.onPickUp();
    }

    public abstract void onPickUp();

    public void activateBox(Room room) {
        this.activateBox(room, (RoomUnit) null, 0L);
    }

    public void activateBox(Room room, RoomUnit roomUnit, long millis) {
        this.flashBox(room);
        this.recordExecution(roomUnit, millis);
    }

    /** Flips the box's light; the room cycle sends every flipped box together, once per 500 ms. */
    public void flashBox(Room room) {
        if (room == null || room.isHideWired() || room.getWiredRuntime() == null) {
            return;
        }

        room.getWiredRuntime().getBoxFlashes().flip(this, WiredBoxFlashes::toggle);
    }

    /** Remembers that this box ran for the user, for its per-user cooldown, without flashing it. */
    public void recordExecution(RoomUnit roomUnit, long millis) {
        if (roomUnit != null) {
            this.addUserExecutionCache(roomUnit.getId(), millis);
        }
    }

    protected long requiredCooldown() {
        return 50L;
    }

    public boolean canExecute(long newMillis) {
        return newMillis - this.cooldown >= this.requiredCooldown();
    }

    public void setCooldown(long newMillis) {
        this.cooldown = newMillis;
    }

    /** Claims exclusive processing of this box; returns false if another thread is already in it. */
    public boolean tryBeginProcessing() {
        return this.processing.compareAndSet(false, true);
    }

    public void endProcessing() {
        this.processing.set(false);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean isUsable() {
        return true;
    }

    public boolean userCanExecute(int roomUnitId, long timestamp) {
        if (roomUnitId == -1) {
            return true;
        } else {
            if (this.userExecutionCache.containsKey((long) roomUnitId)) {
                long lastTimestamp = this.userExecutionCache.get((long) roomUnitId);
                return timestamp - lastTimestamp >= Math.max(100L, this.requiredCooldown());
            }

            return true;
        }
    }

    public void clearUserExecutionCache() {
        this.userExecutionCache.clear();
    }

    public void addUserExecutionCache(int roomUnitId, long timestamp) {
        // Enforce max size limit to prevent memory leaks
        if (this.userExecutionCache.size() >= MAX_USER_CACHE_SIZE) {
            cleanExpiredCacheEntries(timestamp);

            // If still too large after cleanup, remove oldest entries
            if (this.userExecutionCache.size() >= MAX_USER_CACHE_SIZE) {
                // Remove approximately 10% of entries
                int toRemove = MAX_USER_CACHE_SIZE / 10;
                Iterator<Map.Entry<Long, Long>> iterator =
                        this.userExecutionCache.entrySet().iterator();
                while (iterator.hasNext() && toRemove > 0) {
                    iterator.next();
                    iterator.remove();
                    toRemove--;
                }
            }
        }
        this.userExecutionCache.put((long) roomUnitId, timestamp);
    }

    /**
     * Removes cache entries older than CACHE_EXPIRY_MS.
     * @param currentTimestamp the current timestamp to compare against
     */
    public void cleanExpiredCacheEntries(long currentTimestamp) {
        this.userExecutionCache.entrySet().removeIf(entry -> currentTimestamp - entry.getValue() > CACHE_EXPIRY_MS);
    }

    /**
     * Gets the current size of the user execution cache.
     * @return the number of cached entries
     */
    public int getUserExecutionCacheSize() {
        return this.userExecutionCache.size();
    }

    public static WiredSettings readSettings(ClientMessage packet, boolean isEffect) {
        return readSettings(packet, isEffect, WiredInputGuard.MAX_STRING_PARAM_LENGTH);
    }

    public static WiredSettings readSettings(ClientMessage packet, boolean isEffect, int maximumStringLength) {
        return readSettings(packet, isEffect ? SaveKind.ACTION : SaveKind.TRIGGER, maximumStringLength);
    }

    /** Which official save packet is being read; only the slot after the furni picks differs. */
    public enum SaveKind {
        TRIGGER,
        ACTION,
        CONDITION,
        ADDON,
        SELECTOR,
        VARIABLE
    }

    /**
     * Reads the official AIR save layout (build WIN63-202610131318), after the wired id: {@code int[]
     * intParams, String stringParam, int[] stuffIds}, the kind's slot (action {@code int delay}, condition
     * {@code int quantifierCode}, selector {@code bool filter, bool invert}), then {@code int[] furniSources, int[] userSources, String[] variableIds,
     * int[] stuffIds2}.
     */
    public static WiredSettings readSettings(ClientMessage packet, SaveKind kind, int maximumStringLength) {
        int[] intParams = WiredInputGuard.readIntParams(packet);
        String stringParam = WiredInputGuard.readStringParam(packet, maximumStringLength);
        int pickCount = peekCount(packet);
        int[] itemIds = WiredInputGuard.readFurniIds(packet);

        WiredSettings settings = new WiredSettings(intParams, stringParam, itemIds, -1);

        switch (kind) {
            case ACTION -> {
                int delay = packet.readInt();
                settings.setRawDelay(delay);
                settings.setDelay(WiredInputGuard.normalizeDelay(delay));
            }
            case CONDITION -> settings.setQuantifierCode(packet.readInt());
            case SELECTOR -> {
                settings.setSelectorFilter(packet.readBoolean());
                settings.setSelectorInvert(packet.readBoolean());
            }
            default -> {}
        }

        settings.setFurniSourceTypes(WiredInputGuard.readSourceTypes(packet));
        settings.setUserSourceTypes(WiredInputGuard.readSourceTypes(packet));
        settings.setVariableIds(WiredInputGuard.readVariableIds(packet));
        int pickCount2 = peekCount(packet);
        settings.setFurniIds2(WiredInputGuard.readFurniIds(packet));
        settings.setRawPickCounts(pickCount, Math.max(0, pickCount2));
        return settings;
    }

    /** The count a list starts with, as sent (repeats and dead ids included), without reading it. */
    private static int peekCount(ClientMessage packet) {
        if (packet == null || packet.getBuffer() == null || packet.getBuffer().readableBytes() < 4) {
            return 0;
        }

        return packet.getBuffer().getInt(packet.getBuffer().readerIndex());
    }
}
