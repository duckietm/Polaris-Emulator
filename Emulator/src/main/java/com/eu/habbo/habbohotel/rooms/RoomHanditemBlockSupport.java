package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.interactions.InteractionHanditemBlockControl;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.ConfigurationItemStatesComposer;
import com.eu.habbo.messages.outgoing.rooms.items.HanditemBlockStateComposer;

public final class RoomHanditemBlockSupport {
    private static final String CONTROLLER_INTERACTION = "wf_conf_handitem_block";

    private RoomHanditemBlockSupport() {}

    public static boolean isHanditemBlocked(Room room) {
        if (room == null) {
            return false;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (isActiveController(item)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActiveController(HabboItem item) {
        return isControllerItem(item) && "1".equals(item.getExtradata());
    }

    public static boolean isControllerItem(HabboItem item) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        if (item instanceof InteractionHanditemBlockControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }

    public static void sendState(Room room) {
        if (room == null) {
            return;
        }

        room.sendComposer(new HanditemBlockStateComposer(room).compose());
        broadcastConfigurationItemStates(room);
    }

    /**
     * AIR 13 {@code ConfigurationItemStates} (1508) carries per-viewer flags, so
     * a room-wide refresh sends one packet per habbo.
     */
    static void broadcastConfigurationItemStates(Room room) {
        if (room == null) {
            return;
        }

        for (Habbo habbo : room.getHabbos()) {
            if (habbo.getClient() != null) {
                habbo.getClient().sendResponse(new ConfigurationItemStatesComposer(room, habbo).compose());
            }
        }
    }

    public static void sendState(Room room, GameClient client) {
        if (room == null || client == null) {
            return;
        }

        client.sendResponse(new HanditemBlockStateComposer(room).compose());
    }
}
