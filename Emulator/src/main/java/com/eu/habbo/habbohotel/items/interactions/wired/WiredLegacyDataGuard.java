package com.eu.habbo.habbohotel.items.interactions.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.util.ArrayList;
import java.util.List;

public final class WiredLegacyDataGuard {
    public static final int DEFAULT_MAX_DELAY = 20;

    private WiredLegacyDataGuard() {
    }

    public static int parseDelay(String value) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return Math.max(0, Math.min(parsed, DEFAULT_MAX_DELAY));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static List<HabboItem> parseRoomItems(String value, Room room) {
        List<HabboItem> items = new ArrayList<>();
        if (room == null || value == null || value.isBlank()) {
            return items;
        }

        for (String part : value.split(";")) {
            try {
                int itemId = Integer.parseInt(part.trim());
                if (itemId <= 0) {
                    continue;
                }

                HabboItem item = room.getHabboItem(itemId);
                if (item != null) {
                    items.add(item);
                }
            } catch (NumberFormatException e) {
                // Ignore malformed legacy ids and keep loading the remaining items.
            }
        }

        return items;
    }
}
