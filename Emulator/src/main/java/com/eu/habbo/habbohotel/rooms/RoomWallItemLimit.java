package com.eu.habbo.habbohotel.rooms;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The wall furni cap of a room ({@code hotel.room.wallfurni.max}), apart from the room furni cap. Habbo counts
 * floor and wall furni against their own caps (4000 each); a room already over the cap keeps its furni, only
 * new wall furni is refused.
 */
public final class RoomWallItemLimit {

    /** Habbo's wall furni cap. */
    public static final int DEFAULT_MAX_WALL_ITEMS = 4000;

    private static final AtomicInteger MAX_WALL_ITEMS = new AtomicInteger(DEFAULT_MAX_WALL_ITEMS);

    private RoomWallItemLimit() {}

    public static int maxWallItems() {
        return MAX_WALL_ITEMS.get();
    }

    /** Sets the cap; a negative cap counts as 0. */
    public static void configure(int maxWallItems) {
        MAX_WALL_ITEMS.set(Math.max(0, maxWallItems));
    }

    /** Whether one more wall furni fits next to {@code wallItemCount}. */
    public static boolean hasRoomFor(int wallItemCount) {
        return wallItemCount < MAX_WALL_ITEMS.get();
    }
}
