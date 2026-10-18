package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The official Builders Club furni id band and the translation between it and database ids.
 *
 * <p>AIR tells a Builders Club furni only by its object id ({@code habbo/utils/FurniId.isBuilderClubId}: {@code
 * 2147418112..2147483647}) and then shows the warehouse instead of an owner on the infostand, asks before picking
 * up a furni that is not in the warehouse and skips the fly-to-inventory animation. The emulator keeps database ids
 * everywhere; a Builders Club furni gets a wire id from the band while it is in a loaded room (one allocation per
 * room, see {@code RoomBuildersClubIdBand}) and every packet translates at the boundary: composers write {@link
 * #toWire}, handlers read through {@link #fromWire}. Normal and temporary (Wired) furni are never in the band and
 * pass through unchanged.
 */
public final class BuildersClubItemIds {
    /** First id of the official Builders Club band. */
    public static final int FIRST = 2_147_418_112;
    /** Last id of the band, the int maximum. */
    public static final int LAST = Integer.MAX_VALUE;
    /** Ids in the band. */
    public static final int SIZE = LAST - FIRST + 1;

    /**
     * Wire id by database id of every Builders Club furni a loaded room handed a band id to. Database ids are unique
     * hotel-wide, so composers can translate without knowing the room. An entry outlives the furni's stay in the room
     * until its slot is handed out again or the room unloads, so removal packets built afterwards still match.
     */
    private static final ConcurrentHashMap<Integer, Integer> WIRE_BY_ITEM = new ConcurrentHashMap<>();

    private BuildersClubItemIds() {}

    /** Whether {@code id} lies in the official Builders Club band. */
    public static boolean isBandId(int id) {
        return id >= FIRST;
    }

    /** The id the client knows the furni with database id {@code itemId} by. */
    public static int toWire(int itemId) {
        if (itemId <= 0 || WIRE_BY_ITEM.isEmpty()) {
            return itemId;
        }
        Integer wireId = WIRE_BY_ITEM.get(itemId);
        return wireId != null ? wireId : itemId;
    }

    /** The id the client knows {@code item} by; 0 for no item. */
    public static int toWire(HabboItem item) {
        return item == null ? 0 : toWire(item.getId());
    }

    /** {@link #toWire(int)} for every id. */
    public static int[] toWire(int[] itemIds) {
        if (itemIds == null) {
            return new int[0];
        }
        int[] wireIds = new int[itemIds.length];
        for (int i = 0; i < itemIds.length; i++) {
            wireIds[i] = toWire(itemIds[i]);
        }
        return wireIds;
    }

    /**
     * The database id behind an id the client sent while in {@code room}. Ids outside the band come back unchanged;
     * a band id the room does not know comes back as 0, which no furni has.
     */
    public static int fromWire(Room room, int wireId) {
        if (!isBandId(wireId)) {
            return wireId;
        }
        if (room == null || room.getItemManager() == null) {
            return 0;
        }
        return room.getItemManager().itemIdFromWire(wireId);
    }

    /**
     * {@link #fromWire(Room, int)} for a field that holds a furni id only when {@code furni} is true (a user's room
     * index or a room id otherwise, which are never translated).
     */
    public static int fromWire(Room room, int wireId, boolean furni) {
        return furni ? fromWire(room, wireId) : wireId;
    }

    /** {@link #toWire(int)} for a field that holds a furni id only when {@code furni} is true. */
    public static int toWire(int itemId, boolean furni) {
        return furni ? toWire(itemId) : itemId;
    }

    /** {@link #fromWire(Room, int)} for every id. */
    public static int[] fromWire(Room room, int[] wireIds) {
        if (wireIds == null) {
            return new int[0];
        }
        int[] itemIds = new int[wireIds.length];
        for (int i = 0; i < wireIds.length; i++) {
            itemIds[i] = fromWire(room, wireIds[i]);
        }
        return itemIds;
    }

    /** Translates both furni picks of a wired save read from the client, so the box stores database ids. */
    public static WiredSettings fromWire(WiredSettings settings, Room room) {
        if (settings != null) {
            settings.setFurniIds(fromWire(room, settings.getFurniIds()));
            settings.setFurniIds2(fromWire(room, settings.getFurniIds2()));
        }
        return settings;
    }

    static void publish(int itemId, int wireId) {
        WIRE_BY_ITEM.put(itemId, wireId);
    }

    static void unpublish(int itemId, int wireId) {
        WIRE_BY_ITEM.remove(itemId, wireId);
    }
}
