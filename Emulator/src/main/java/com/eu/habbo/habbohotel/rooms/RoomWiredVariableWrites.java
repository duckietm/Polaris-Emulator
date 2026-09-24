package com.eu.habbo.habbohotel.rooms;

import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectGiveVariable;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * The value writes of the wired creator tools (variable manage and update), shared by the wired
 * menu packets and the variables web API. Callers set the change origin; every write goes through
 * the room's variable managers, so persistence, triggers and client updates follow as usual.
 *
 * <p>Target types are the creator-tool packet codes: furni, room, and anything else is a user.
 */
public final class RoomWiredVariableWrites {
    public static final int TARGET_USER = WiredEffectGiveVariable.TARGET_USER;
    public static final int TARGET_FURNI = WiredEffectGiveVariable.TARGET_FURNI;
    public static final int TARGET_ROOM = 3;

    private RoomWiredVariableWrites() {}

    /** Gives the holder the variable with this value, replacing any value it had. */
    public static boolean assign(Room room, int targetType, int targetId, int definitionItemId, int value) {
        if (targetType == TARGET_FURNI) {
            HabboItem furni = room.getHabboItem(targetId);
            return furni != null && room.getFurniVariableManager().assignVariable(furni, definitionItemId, value, true);
        }
        if (targetType == TARGET_ROOM) {
            return room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
        }
        Habbo habbo = room.getHabbo(targetId);
        return habbo != null && room.getUserVariableManager().assignVariable(habbo, definitionItemId, value, true);
    }

    /** Changes the value of a variable the holder already has. */
    public static boolean update(Room room, int targetType, int targetId, int definitionItemId, int value) {
        if (targetType == TARGET_FURNI) {
            return room.getFurniVariableManager().updateVariableValue(targetId, definitionItemId, value);
        }
        if (targetType == TARGET_ROOM) {
            return room.getRoomVariableManager().updateVariableValue(definitionItemId, value);
        }
        return room.getUserVariableManager().updateVariableValue(targetId, definitionItemId, value);
    }

    public static boolean remove(Room room, int targetType, int targetId, int definitionItemId) {
        if (targetType == TARGET_FURNI) {
            return room.getFurniVariableManager().removeVariable(targetId, definitionItemId);
        }
        if (targetType == TARGET_ROOM) {
            return room.getRoomVariableManager().removeVariable(definitionItemId);
        }
        return room.getUserVariableManager().removeVariable(targetId, definitionItemId);
    }

    /** Takes the variable from every holder; answers how many lost it. Room variables are left alone. */
    public static int clearAll(Room room, int targetType, int definitionItemId) {
        if (targetType == TARGET_FURNI) {
            return room.getFurniVariableManager().clearAllAssignments(definitionItemId);
        }
        if (targetType == TARGET_ROOM) {
            return 0;
        }
        return room.getUserVariableManager().clearAllAssignments(definitionItemId);
    }
}
