package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionBackgroundToner;
import com.eu.habbo.habbohotel.users.HabboItem;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.awt.Color;

final class RoomVisualSettings {

    private static final Color DEFAULT_TONER_COLOR = new Color(0, 0, 0);

    private RoomVisualSettings() {}

    static Color backgroundTonerColor(RoomItemManager itemManager) {
        Int2ObjectMap<HabboItem> items = itemManager.getRoomItems();

        synchronized (items) {
            for (HabboItem item : items.values()) {
                Color color = enabledTonerColor(item);
                if (color != null) {
                    return color;
                }
            }
        }

        return DEFAULT_TONER_COLOR;
    }

    private static Color enabledTonerColor(HabboItem item) {
        if (!(item instanceof InteractionBackgroundToner)) {
            return null;
        }

        String[] extraData = item.getExtradata().split(":");
        if (extraData.length != 4 || !extraData[0].equalsIgnoreCase("1")) {
            return null;
        }

        return Color.getHSBColor(
                Integer.parseInt(extraData[1]), Integer.parseInt(extraData[2]), Integer.parseInt(extraData[3]));
    }
}
