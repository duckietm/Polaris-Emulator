package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionAreaHideControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.AreaHideComposer;

public final class RoomAreaHideSupport {
    private RoomAreaHideSupport() {
    }

    public static boolean isControllerItem(HabboItem item) {
        return item instanceof InteractionAreaHideControl
            || hasInteractionName(item, "wf_conf_area_hide")
            || hasInteractionName(item, "conf_area_hide");
    }

    public static boolean isControllerActive(HabboItem item) {
        return isControllerItem(item) && getState(item) == 1;
    }

    public static int getState(HabboItem item) {
        return Math.min(1, readIntValue(item, "state", 0));
    }

    public static int getRootX(HabboItem item) {
        return readIntValue(item, "rootX", 0);
    }

    public static int getRootY(HabboItem item) {
        return readIntValue(item, "rootY", 0);
    }

    public static int getWidth(HabboItem item) {
        return readIntValue(item, "width", 0);
    }

    public static int getLength(HabboItem item) {
        return readIntValue(item, "length", 0);
    }

    public static boolean isInvisibilityEnabled(HabboItem item) {
        return readIntValue(item, "invisibility", 0) == 1;
    }

    public static boolean includesWallItems(HabboItem item) {
        return readIntValue(item, "wallItems", 0) == 1;
    }

    public static boolean isInverted(HabboItem item) {
        return readIntValue(item, "invert", 0) == 1;
    }

    public static void sendState(Room room, HabboItem item) {
        if (room == null || item == null || !isControllerItem(item)) {
            return;
        }

        room.sendComposer(new AreaHideComposer(item).compose());
    }

    public static void sendState(Room room, GameClient client) {
        if (room == null || client == null) {
            return;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (!isControllerActive(item)) {
                continue;
            }

            client.sendResponse(new AreaHideComposer(item).compose());
        }
    }

    private static int readIntValue(HabboItem item, String key, int fallback) {
        if (!(item instanceof InteractionAreaHideControl areaHide) || key == null) {
            return fallback;
        }

        String value = areaHide.values.get(key);

        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean hasInteractionName(HabboItem item, String interactionName) {
        return item != null
            && item.getBaseItem() != null
            && item.getBaseItem().getType() == FurnitureType.FLOOR
            && item.getBaseItem().getInteractionType() != null
            && item.getBaseItem().getInteractionType().getName() != null
            && item.getBaseItem().getInteractionType().getName().equalsIgnoreCase(interactionName);
    }
}
