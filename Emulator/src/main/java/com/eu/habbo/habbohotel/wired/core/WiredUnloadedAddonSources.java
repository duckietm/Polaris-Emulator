package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraRoomVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraTimeUtilities;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraUserVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableAddonShare;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableLevelUpSystem;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableTextConnector;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The shared variable boxes of source rooms that are not loaded, with the boxes on their tile, read from the saved
 * furni for {@link WiredVariableAddonSharing}. A box is only saved, moved or picked up while its room is loaded, and
 * every such change invalidates the room ({@link WiredManager#invalidateRoom}), which drops what is cached for it
 * here; an entry also expires after {@link #TTL_MS}. Only the boxes sharing reads are built, detached from any room,
 * and they are never written back.
 */
final class WiredUnloadedAddonSources {
    private static final Logger LOGGER = LoggerFactory.getLogger(WiredUnloadedAddonSources.class);

    static final long TTL_MS = 60_000;
    static final int MAX_ENTRIES = 1_000;
    /** Saved boxes read from one tile at most. */
    static final int MAX_TILE_ITEMS = 64;

    private static final List<Class<? extends InteractionWiredExtra>> READ_TYPES = List.of(
            WiredExtraUserVariable.class,
            WiredExtraRoomVariable.class,
            WiredExtraVariableAddonShare.class,
            WiredExtraVariableTextConnector.class,
            WiredExtraVariableLevelUpSystem.class,
            WiredExtraTimeUtilities.class,
            WiredExtraVariableFx.class);

    private static final String TILE_SQL = "SELECT items.*, rooms.owner_id AS source_owner_id FROM items "
            + "INNER JOIN items shared_box ON shared_box.room_id = items.room_id "
            + "AND shared_box.x = items.x AND shared_box.y = items.y "
            + "INNER JOIN rooms ON rooms.id = shared_box.room_id "
            + "WHERE shared_box.id = ? AND shared_box.room_id = ? AND items.wired_data <> '' "
            + "ORDER BY items.z, items.id LIMIT " + MAX_TILE_ITEMS;

    /**
     * What a source room has saved for one variable: its owner, the variable box (null when it is gone) and the
     * boxes on its tile in execution order.
     */
    record Saved(int ownerId, InteractionWiredExtra source, List<InteractionWiredExtra> tile) {
        static final Saved MISSING = new Saved(0, null, List.of());

        Saved {
            tile = List.copyOf(tile);
        }
    }

    interface Loader {
        Saved load(int sourceRoomId, int sourceItemId) throws SQLException;
    }

    private record CachedSaved(Saved saved, long expiresAt) {}

    private static final Map<Long, CachedSaved> CACHE =
            Collections.synchronizedMap(new LinkedHashMap<Long, CachedSaved>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, CachedSaved> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    private static final AtomicReference<Loader> LOADER =
            new AtomicReference<>(WiredUnloadedAddonSources::loadFromDatabase);
    private static final AtomicReference<LongSupplier> CLOCK = new AtomicReference<>(System::currentTimeMillis);

    private WiredUnloadedAddonSources() {}

    /** The saved source of a variable, read once per {@link #TTL_MS}; {@link Saved#MISSING} when unreadable. */
    static Saved get(int sourceRoomId, int sourceItemId) {
        long key = key(sourceRoomId, sourceItemId);
        long now = CLOCK.get().getAsLong();
        CachedSaved entry = CACHE.get(key);
        if (entry != null && entry.expiresAt() > now) return entry.saved();

        Saved saved;
        try {
            saved = LOADER.get().load(sourceRoomId, sourceItemId);
        } catch (SQLException e) {
            LOGGER.error("Failed to read the shared add-ons of variable {} in room {}", sourceItemId, sourceRoomId, e);
            saved = null;
        }
        if (saved == null) saved = Saved.MISSING;

        CACHE.put(key, new CachedSaved(saved, now + TTL_MS));
        return saved;
    }

    /** Forgets everything read from {@code roomId}. */
    static void invalidate(int roomId) {
        synchronized (CACHE) {
            CACHE.keySet().removeIf(key -> (int) (key >>> 32) == roomId);
        }
    }

    static void useForTests(Loader testLoader, LongSupplier testClock) {
        LOADER.set(testLoader);
        CLOCK.set(testClock);
        CACHE.clear();
    }

    static void reset() {
        useForTests(WiredUnloadedAddonSources::loadFromDatabase, System::currentTimeMillis);
    }

    private static long key(int roomId, int itemId) {
        return ((long) roomId << 32) | (itemId & 0xFFFFFFFFL);
    }

    private static Saved loadFromDatabase(int sourceRoomId, int sourceItemId) throws SQLException {
        if (WiredPlatform.database() == null
                || WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getItemManager() == null) {
            return Saved.MISSING;
        }

        ItemManager items = WiredPlatform.gameEnvironment().getItemManager();
        int ownerId = 0;
        InteractionWiredExtra source = null;
        List<InteractionWiredExtra> tile = new ArrayList<>();

        try (Connection connection = WiredPlatform.database().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(TILE_SQL)) {
            statement.setInt(1, sourceItemId);
            statement.setInt(2, sourceRoomId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    ownerId = set.getInt("source_owner_id");

                    InteractionWiredExtra extra = read(items, set);
                    if (extra == null) continue;

                    tile.add(extra);
                    if (extra.getId() == sourceItemId) source = extra;
                }
            }
        }
        return new Saved(ownerId, source, WiredExecutionOrderUtil.sort(tile));
    }

    /** One saved box of a type sharing reads, with its wired data; null for anything else or a malformed row. */
    private static InteractionWiredExtra read(ItemManager items, ResultSet set) {
        int itemId = 0;
        try {
            itemId = set.getInt("id");
            Item base = items.getItem(set.getInt("item_id"));
            if (base == null
                    || base.getInteractionType() == null
                    || !isRead(base.getInteractionType().getType())) {
                return null;
            }

            HabboItem item = items.loadHabboItem(set);
            if (!(item instanceof InteractionWiredExtra extra)) return null;

            extra.loadWiredData(set, null);
            return extra;
        } catch (Exception e) {
            LOGGER.warn("Skipped malformed saved wired box {}", itemId, e);
            return null;
        }
    }

    static boolean isRead(Class<?> type) {
        if (type == null) return false;

        for (Class<? extends InteractionWiredExtra> read : READ_TYPES) {
            if (read.isAssignableFrom(type)) return true;
        }
        return false;
    }
}
