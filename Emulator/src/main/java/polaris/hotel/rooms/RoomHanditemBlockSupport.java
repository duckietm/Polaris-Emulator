package polaris.hotel.rooms;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.interactions.InteractionHanditemBlockControl;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.ConfigurationItemStatesComposer;
import polaris.messages.outgoing.rooms.items.HanditemBlockStateComposer;

public final class RoomHanditemBlockSupport {
    private static final String CONTROLLER_INTERACTION = "wf_conf_handitem_block";

    private RoomHanditemBlockSupport() {}

    public static boolean isHanditemBlocked(Room room) {
        if (room == null) {
            return false;
        }

        for (UserItem item : room.getFloorItems()) {
            if (isActiveController(item)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isActiveController(UserItem item) {
        return isControllerItem(item) && "1".equals(item.getExtradata());
    }

    public static boolean isControllerItem(UserItem item) {
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
     * a room-wide refresh sends one packet per user.
     */
    static void broadcastConfigurationItemStates(Room room) {
        if (room == null) {
            return;
        }

        for (User user : room.getUsers()) {
            if (user.getClient() != null) {
                user.getClient().sendResponse(new ConfigurationItemStatesComposer(room, user).compose());
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
