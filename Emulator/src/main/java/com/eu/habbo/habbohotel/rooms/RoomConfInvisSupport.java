package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.interactions.InteractionConfInvisControl;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.items.ConfInvisStateComposer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class RoomConfInvisSupport {
    private RoomConfInvisSupport() {
    }

    public static boolean isControllerItem(HabboItem item) {
        return item instanceof InteractionConfInvisControl
            || hasInteractionName(item, "wf_conf_invis_control");
    }

    public static boolean isControllerActive(HabboItem item) {
        return isControllerItem(item) && "1".equals(item.getExtradata());
    }

    public static boolean isTarget(HabboItem item) {
        return item != null
            && item.getBaseItem() != null
            && item.getBaseItem().getType() == FurnitureType.FLOOR
            && hasCustomParamToken(item.getBaseItem().getCustomParams(), "is_invisible");
    }

    public static List<Integer> collectHiddenFloorItemIds(Room room) {
        List<Integer> hiddenItemIds = new ArrayList<>();

        if (room == null) {
            return hiddenItemIds;
        }

        if (!hasActiveController(room)) {
            return hiddenItemIds;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (isTarget(item)) {
                hiddenItemIds.add(item.getId());
            }
        }

        return hiddenItemIds;
    }

    public static boolean hasActiveController(Room room) {
        if (room == null) {
            return false;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (isControllerActive(item)) {
                return true;
            }
        }

        return false;
    }

    public static void sendState(Room room) {
        if (room == null) {
            return;
        }

        room.sendComposer(new ConfInvisStateComposer(room).compose());
    }

    public static void sendState(Room room, GameClient client) {
        if (room == null || client == null) {
            return;
        }

        client.sendResponse(new ConfInvisStateComposer(room).compose());
    }

    private static boolean hasCustomParamToken(String value, String token) {
        if (value == null || token == null) {
            return false;
        }

        String normalized = value.trim().toLowerCase();

        if (normalized.isEmpty()) {
            return false;
        }

        Pattern pattern = Pattern.compile("(^|[^a-z0-9_])" + Pattern.quote(token.toLowerCase()) + "($|[^a-z0-9_])");

        return pattern.matcher(normalized).find();
    }

    private static boolean hasInteractionName(HabboItem item, String interactionName) {
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getInteractionType() == null || interactionName == null) {
            return false;
        }

        String currentInteractionName = item.getBaseItem().getInteractionType().getName();

        return currentInteractionName != null && currentInteractionName.equalsIgnoreCase(interactionName);
    }
}
