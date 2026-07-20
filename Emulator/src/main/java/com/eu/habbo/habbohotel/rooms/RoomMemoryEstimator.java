package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.wired.tick.WiredTickService;

final class RoomMemoryEstimator {

    private static final long BASE_BYTES = 10 * 1024L;
    private static final long ITEM_BYTES = 512L;
    private static final long USER_BYTES = 2048L;
    private static final long MAP_TILE_BYTES = 128L;
    private static final long WIRED_TICKABLE_BYTES = 256L;

    private RoomMemoryEstimator() {}

    static long estimate(Room room) {
        long bytes = BASE_BYTES;
        RoomItemManager itemManager = room.getItemManager();
        if (itemManager != null) {
            bytes += itemManager.itemCount() * ITEM_BYTES;
        }
        bytes += room.getUserCount() * USER_BYTES;

        RoomLayout layout = room.getLayout();
        if (layout != null) {
            bytes += layout.getMapSize() * MAP_TILE_BYTES;
        }

        WiredTickService wired = WiredTickService.getInstance();
        if (wired != null) {
            bytes += wired.getTickableCount(room.getId()) * WIRED_TICKABLE_BYTES;
        }
        return bytes;
    }
}
