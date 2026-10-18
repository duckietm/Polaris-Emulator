package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionChooserDisableControl;
import com.eu.habbo.habbohotel.users.HabboItem;

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
