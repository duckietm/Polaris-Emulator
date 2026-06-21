package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

final class WiredEffectPayloadGuard {
    static final int MAX_LOAD_DELAY = 3600;

    private WiredEffectPayloadGuard() {
    }

    static int delay(int value) {
        if (value < 0) {
            return 0;
        }

        return Math.min(value, MAX_LOAD_DELAY);
    }

    static int parseDelay(String value) {
        return delay(parseInt(value, 0));
    }

    static int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    static int mode(int value) {
        return value == 1 ? 1 : 0;
    }

    static int furniSource(int value) {
        switch (value) {
            case WiredSourceUtil.SOURCE_TRIGGER:
            case WiredSourceUtil.SOURCE_SELECTED:
            case WiredSourceUtil.SOURCE_SELECTOR:
            case WiredSourceUtil.SOURCE_SIGNAL:
                return value;
            default:
                return WiredSourceUtil.SOURCE_TRIGGER;
        }
    }

    static String text(String value) {
        return value == null ? "" : value.replace("\t", "");
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
