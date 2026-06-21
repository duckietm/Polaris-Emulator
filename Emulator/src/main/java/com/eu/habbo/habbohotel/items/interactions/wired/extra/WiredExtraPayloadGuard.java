package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

final class WiredExtraPayloadGuard {
    private WiredExtraPayloadGuard() {
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
