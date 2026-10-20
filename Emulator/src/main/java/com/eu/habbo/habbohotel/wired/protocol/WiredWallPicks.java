package com.eu.habbo.habbohotel.wired.protocol;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.wired.WiredSettings;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Arrays;

/**
 * Wall furni picks as AIR sends them: a picked wall item travels as its negative id ({@code getFurni(-id, category
 * 20)}), in both pick lists of a save and of a definition. Boxes keep storing database ids, so a negative pick is
 * resolved to its wall item here and a stored wall pick goes out negative again.
 *
 * <p>A box takes wall picks when its {@code InputSourcesConf} allows wall furni, or when it has no conf at all (the
 * editor then accepts wall picks as before). A positive id that is a wall item is the way boxes stored wall picks
 * before; it keeps resolving as before, and a box that does not take wall picks sends it back positive.
 */
final class WiredWallPicks {
    private WiredWallPicks() {}

    /** Whether the box takes wall furni picks. */
    static boolean allowsWallFurni(Object box) {
        WiredInputSourcesConf conf = WiredInputSourcesConf.of(box);
        return conf.allowWallFurni() || (conf.allowedFurni().length == 0 && conf.allowedUsers().length == 0);
    }

    /** Resolves the negative picks of both pick lists of a save to the database ids of their wall items. */
    static void fromWire(Object box, WiredSettings settings, Room room) {
        if (settings == null) return;
        boolean allowed = allowsWallFurni(box);
        settings.setFurniIds(fromWire(settings.getFurniIds(), room, allowed));
        settings.setFurniIds2(fromWire(settings.getFurniIds2(), room, allowed));
    }

    /**
     * The picks with every negative id replaced by the database id of the wall item it names. A negative id that
     * names no wall item of the room, or comes for a box that takes no wall picks, is dropped.
     */
    static int[] fromWire(int[] picks, Room room, boolean allowed) {
        if (picks == null) return new int[0];

        int[] resolved = new int[picks.length];
        int count = 0;
        for (int pick : picks) {
            if (pick > 0) {
                resolved[count++] = pick;
                continue;
            }
            if (!allowed || pick == 0 || pick == Integer.MIN_VALUE || room == null) continue;

            int itemId = BuildersClubItemIds.fromWire(room, -pick);
            HabboItem item = itemId > 0 ? room.getHabboItem(itemId) : null;
            if (isWallItem(item) && !contains(resolved, count, itemId)) {
                resolved[count++] = itemId;
            }
        }
        return count == resolved.length ? resolved : Arrays.copyOf(resolved, count);
    }

    /**
     * The picks as the client knows them: Builders Club band ids, and wall items negative when the box takes wall
     * picks.
     */
    static int[] toWire(int[] picks, Object box) {
        return toWire(picks, box, null);
    }

    /** {@link #toWire(int[], Object)} in a known room; null looks the box's room up. */
    static int[] toWire(int[] picks, Object box, Room knownRoom) {
        int[] wire = BuildersClubItemIds.toWire(picks);
        if (wire.length == 0 || !allowsWallFurni(box)) return wire;

        Room room = knownRoom != null ? knownRoom : roomOf(box);
        if (room == null) return wire;

        for (int i = 0; i < wire.length; i++) {
            if (picks[i] > 0 && isWallItem(room.getHabboItem(picks[i]))) {
                wire[i] = -wire[i];
            }
        }
        return wire;
    }

    private static boolean isWallItem(HabboItem item) {
        return item != null && item.getBaseItem() != null && item.getBaseItem().getType() == FurnitureType.WALL;
    }

    private static boolean contains(int[] values, int count, int value) {
        for (int i = 0; i < count; i++) {
            if (values[i] == value) return true;
        }
        return false;
    }

    private static Room roomOf(Object box) {
        if (!(box instanceof HabboItem item)
                || WiredPlatform.gameEnvironment() == null
                || WiredPlatform.gameEnvironment().getRoomManager() == null) {
            return null;
        }
        return WiredPlatform.gameEnvironment().getRoomManager().getRoom(item.getRoomId());
    }
}
