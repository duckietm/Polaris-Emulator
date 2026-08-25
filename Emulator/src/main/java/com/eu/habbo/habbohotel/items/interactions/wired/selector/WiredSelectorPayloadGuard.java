package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.wired.core.WiredManager;

final class WiredSelectorPayloadGuard {
    private WiredSelectorPayloadGuard() {
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
