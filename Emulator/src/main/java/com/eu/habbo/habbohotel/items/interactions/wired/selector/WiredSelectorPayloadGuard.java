package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

final class WiredSelectorPayloadGuard {
    private WiredSelectorPayloadGuard() {}

    /** The largest x, y, width or height an area selector keeps; AIR stores every value clamped to 0..64. */
    static final int MAX_AREA_VALUE = 64;

    /** An area selector's value at this index clamped to 0..64, 0 when the save left it out. */
    static int areaValue(int[] params, int index) {
        if (params == null || index >= params.length) {
            return 0;
        }

        return Math.max(0, Math.min(MAX_AREA_VALUE, params[index]));
    }

    static <T> T fromJson(String wiredData, Class<T> type) {
        if (wiredData == null || !wiredData.startsWith("{")) {
            return null;
        }

        try {
            return WiredManager.getGson().fromJson(wiredData, type);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
