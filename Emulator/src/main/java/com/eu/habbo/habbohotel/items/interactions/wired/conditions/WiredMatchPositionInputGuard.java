package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredMatchFurniSetting;
import com.eu.habbo.habbohotel.wired.core.WiredManager;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class WiredMatchPositionInputGuard {
    public static final int MAX_STATE_LENGTH = 512;

    private WiredMatchPositionInputGuard() {
    }

    public static int normalizeFurniSource(int value, boolean hasSelectedSettings) {
        int source = switch (value) {
            case WiredSourceUtil.SOURCE_SELECTED, WiredSourceUtil.SOURCE_SELECTOR,
                    WiredSourceUtil.SOURCE_SIGNAL, WiredSourceUtil.SOURCE_TRIGGER -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };

        return (hasSelectedSettings && source == WiredSourceUtil.SOURCE_TRIGGER)
                ? WiredSourceUtil.SOURCE_SELECTED
                : source;
    }

    public static List<WiredMatchFurniSetting> sanitizeSettings(Collection<WiredMatchFurniSetting> settings, Room room) {
        List<WiredMatchFurniSetting> result = new ArrayList<>();
        if (settings == null || room == null) {
            return result;
        }

        for (WiredMatchFurniSetting setting : settings) {
            WiredMatchFurniSetting normalized = sanitizeSetting(setting, room);
            if (normalized != null) {
                result.add(normalized);
            }

            if (result.size() >= WiredManager.MAXIMUM_FURNI_SELECTION) {
                break;
            }
        }

        return result;
    }

    public static WiredMatchFurniSetting sanitizeSetting(WiredMatchFurniSetting setting, Room room) {
        if (setting == null || room == null) {
            return null;
        }

        return sanitizeParts(setting.item_id, setting.state, setting.rotation, setting.x, setting.y, setting.z, room);
    }

    public static WiredMatchFurniSetting sanitizeParts(int itemId, String state, int rotation, int x, int y, double z, Room room) {
        if (itemId < 1 || room == null) {
            return null;
        }

        HabboItem item = room.getHabboItem(itemId);
        if (item == null || rotation < 0 || rotation > 7 || !Double.isFinite(z)) {
            return null;
        }

        if (x < Short.MIN_VALUE || x > Short.MAX_VALUE || y < Short.MIN_VALUE || y > Short.MAX_VALUE) {
            return null;
        }

        if (room.getLayout() != null && room.getLayout().getTile((short) x, (short) y) == null) {
            return null;
        }

        return new WiredMatchFurniSetting(itemId, normalizeState(state), rotation, x, y, z);
    }

    public static String normalizeState(String state) {
        if (state == null) {
            return "";
        }

        String normalized = state.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
        if (normalized.length() > MAX_STATE_LENGTH) {
            return normalized.substring(0, MAX_STATE_LENGTH);
        }

        return normalized;
    }
}
