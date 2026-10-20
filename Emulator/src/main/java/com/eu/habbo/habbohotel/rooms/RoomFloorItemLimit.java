package com.eu.habbo.habbohotel.rooms;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The floor furni cap of a room ({@code hotel.room.floorfurni.max}), next to the wall furni cap
 * ({@link RoomWallItemLimit}). Habbo counts floor and wall furni against their own caps (4000 each) and refuses a new
 * floor furni, a wired box included, once the room holds that many; a room already over the cap keeps its furni.
 *
 * <p>The room furni cap ({@code hotel.room.furni.max}, {@link Room#MAXIMUM_FURNI}) stays in force as the hotel's cap
 * on all furni of a room: a floor furni is refused at whichever of the two is reached first, so the floor line of the
 * wired monitor shows the lower of them ({@link #effectiveMaxFloorItems()}).
 */
public final class RoomFloorItemLimit {

    /** Habbo's floor furni cap. */
    public static final int DEFAULT_MAX_FLOOR_ITEMS = 4000;

    private static final AtomicInteger MAX_FLOOR_ITEMS = new AtomicInteger(DEFAULT_MAX_FLOOR_ITEMS);

    private RoomFloorItemLimit() {}

    public static int maxFloorItems() {
        return MAX_FLOOR_ITEMS.get();
    }

    /** The floor cap bounded by the room furni cap: the most floor furni a room can be given. */
    public static int effectiveMaxFloorItems() {
        return Math.min(MAX_FLOOR_ITEMS.get(), Math.max(0, Room.MAXIMUM_FURNI));
    }

    /** Sets the cap; a negative cap counts as 0. */
    public static void configure(int maxFloorItems) {
        MAX_FLOOR_ITEMS.set(Math.max(0, maxFloorItems));
    }

    /** Whether one more floor furni fits next to {@code floorItemCount}. */
    public static boolean hasRoomFor(int floorItemCount) {
        return floorItemCount < MAX_FLOOR_ITEMS.get();
    }
}
