package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionQueueSpeedControl;
import polaris.hotel.items.interactions.InteractionRoller;
import polaris.hotel.users.UserItem;

public final class RoomQueueSpeedControlSupport {
    private static final String CONTROLLER_INTERACTION = "wf_conf_queue_speed";

    private RoomQueueSpeedControlSupport() {}

    public static Integer getEffectiveRollerSpeed(Room room) {
        UserItem controller = getControllerItem(room);
        if (controller instanceof InteractionQueueSpeedControl speedControl) {
            return InteractionQueueSpeedControl.toRollerSpeed(Integer.toString(speedControl.getRollerModeState()));
        }

        return controller != null ? InteractionQueueSpeedControl.toRollerSpeed(controller.getExtradata()) : null;
    }

    public static int getEffectiveRollerIntervalMs(Room room) {
        Integer effectiveRollerSpeed = getEffectiveRollerSpeed(room);

        if (effectiveRollerSpeed != null) {
            return toRollerIntervalMs(effectiveRollerSpeed);
        }

        if (room == null) {
            return InteractionRoller.DELAY;
        }

        return toRollerIntervalMs(room.getRollerSpeed());
    }

    private static int toRollerIntervalMs(int rollerSpeed) {
        if (rollerSpeed < 0) {
            return InteractionRoller.DELAY;
        }

        return (rollerSpeed + 1) * 500;
    }

    private static boolean isControllerItem(UserItem item) {
        if (item == null || item.getBaseItem() == null) {
            return false;
        }

        if (item instanceof InteractionQueueSpeedControl) {
            return true;
        }

        if (item.getBaseItem().getInteractionType() == null) {
            return false;
        }

        String interactionName = item.getBaseItem().getInteractionType().getName();

        return interactionName != null && interactionName.equalsIgnoreCase(CONTROLLER_INTERACTION);
    }

    private static UserItem getControllerItem(Room room) {
        if (room == null) {
            return null;
        }

        // Asked every room cycle, so it reads the indexed controllers, not a copy of every floor item.
        RoomSpecialTypes specialTypes = room.getRoomSpecialTypes();
        if (specialTypes == null) {
            return null;
        }

        for (UserItem item : specialTypes.getItemsOfType(InteractionQueueSpeedControl.class)) {
            if (!isControllerItem(item)) {
                continue;
            }

            if (item instanceof InteractionQueueSpeedControl) {
                ((InteractionQueueSpeedControl) item).ensureAnimationLoop(room);
            }

            return item;
        }

        return null;
    }
}
