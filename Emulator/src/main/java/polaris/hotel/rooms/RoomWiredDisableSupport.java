package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionWiredDisableControl;
import polaris.hotel.users.UserItem;

public final class RoomWiredDisableSupport {
    private static final String CONTROLLER_INTERACTION = "wf_conf_wired_disable";

    private RoomWiredDisableSupport() {}

    public static boolean isWiredDisabled(Room room) {
        if (room == null) {
            return false;
        }

        // Asked for every wired event, so it reads the indexed controllers, not every floor item.
        RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return false;
        }

        for (UserItem item : specialTypes.getItemsOfType(InteractionWiredDisableControl.class)) {
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

        if (item instanceof InteractionWiredDisableControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }
}
