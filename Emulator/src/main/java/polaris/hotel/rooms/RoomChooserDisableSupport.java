package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionChooserDisableControl;
import polaris.hotel.users.UserItem;

/**
 * The room's {@code :chooser} disabler. AIR closes the user chooser for visitors below rights only while such a
 * furni is switched on ({@code ChatInputWidgetHandler} {@code :chooser}, {@code activeRoomHasChooserDisabled}).
 */
public final class RoomChooserDisableSupport {
    static final String CONTROLLER_INTERACTION = "conf_chooser_disable";

    private RoomChooserDisableSupport() {}

    public static boolean isChooserDisabled(Room room) {
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

        if (item instanceof InteractionChooserDisableControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }
}
