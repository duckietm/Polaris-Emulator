package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionDiceDisableControl;
import polaris.hotel.users.UserItem;

public final class RoomDiceDisableSupport {
    private static final String CONTROLLER_INTERACTION = "conf_dice_disable";

    private RoomDiceDisableSupport() {}

    public static boolean isActive(Room room) {
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

        if (item instanceof InteractionDiceDisableControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }
}
