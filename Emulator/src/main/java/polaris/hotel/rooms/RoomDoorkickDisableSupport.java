package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionDoorkickDisableControl;
import polaris.hotel.users.UserItem;

public final class RoomDoorkickDisableSupport {
    private static final String CONTROLLER_INTERACTION = "conf_doorkick_disable";

    private RoomDoorkickDisableSupport() {}

    public static boolean isDoorkickDisabled(Room room) {
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

        if (item instanceof InteractionDoorkickDisableControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }
}
