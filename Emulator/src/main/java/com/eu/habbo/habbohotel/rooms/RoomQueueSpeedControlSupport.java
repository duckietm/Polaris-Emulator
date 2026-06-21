package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.InteractionQueueSpeedControl;
import com.eu.habbo.habbohotel.items.interactions.InteractionRoller;
import com.eu.habbo.habbohotel.users.HabboItem;

public final class RoomQueueSpeedControlSupport {
    private static final String CONTROLLER_INTERACTION = "wf_conf_queue_speed";

    private RoomQueueSpeedControlSupport() {
    }

    public static Integer getEffectiveRollerSpeed(Room room) {
        HabboItem controller = getControllerItem(room);
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

    private static boolean isControllerItem(HabboItem item) {
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

    private static HabboItem getControllerItem(Room room) {
        if (room == null) {
            return null;
        }

        for (HabboItem item : room.getFloorItems()) {
            if (!isControllerItem(item)) {
                continue;
            }

            if (item instanceof InteractionQueueSpeedControl control) {
                control.ensureAnimationLoop(room);
            }

            return item;
        }

        return null;
    }
}
