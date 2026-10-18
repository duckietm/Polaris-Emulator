package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collections;

/**
 * The variable half of an official value-or-variable input: a variable read once on the first holder
 * of its target that has it. Targets use the official ids: 0 furni (read on a furni source), 1 user
 * (read on a user source), -10 global, -20 context.
 */
public final class WiredValueVariable {
    public static final int TARGET_FURNI = 0;
    public static final int TARGET_USER = 1;
    public static final int TARGET_GLOBAL = -10;
    public static final int TARGET_CONTEXT = -20;

    private WiredValueVariable() {}

    public static int normalizeTarget(int target) {
        return switch (target) {
            case TARGET_USER, TARGET_GLOBAL, TARGET_CONTEXT -> target;
            default -> TARGET_FURNI;
        };
    }

    /** The variable's value, or null when it is not defined or no holder of the target has it. */
    public static Integer read(
            WiredContext ctx, Room room, int target, int variableId, int furniSource, int userSource) {
        return read(ctx, room, target, variableId, furniSource, Collections.emptyList(), userSource);
    }

    /** As {@link #read}, with the box's picked furni for a furni source of picks. */
    public static Integer read(
            WiredContext ctx,
            Room room,
            int target,
            int variableId,
            int furniSource,
            java.util.Collection<HabboItem> picked,
            int userSource) {
        if (room == null || variableId <= 0) return null;
        // A pointer is read as the variable it stands for in this stack.
        variableId = WiredPointerVariableSupport.resolve(ctx, room, variableId);
        if (variableId <= 0) return null;

        switch (target) {
            case TARGET_USER -> {
                if (room.getUserVariableManager().getDefinitionInfo(variableId) == null) return null;
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, userSource)) {
                    int holder = unit == null ? 0 : UserVariableHolders.of(room, unit);
                    if (holder != 0 && room.getUserVariableManager().hasVariable(holder, variableId)) {
                        return room.getUserVariableManager().getCurrentValue(holder, variableId);
                    }
                }
                return null;
            }
            case TARGET_GLOBAL -> {
                return room.getRoomVariableManager().getDefinitionInfo(variableId) == null
                        ? null
                        : room.getRoomVariableManager().getCurrentValue(variableId);
            }
            case TARGET_CONTEXT -> {
                return WiredContextVariableSupport.hasVariable(ctx, variableId)
                        ? WiredContextVariableSupport.getCurrentValue(ctx, variableId)
                        : null;
            }
            default -> {
                if (room.getFurniVariableManager().getDefinitionInfo(variableId) == null) return null;
                for (HabboItem item : WiredSourceUtil.resolveItems(ctx, furniSource, picked)) {
                    if (item != null && room.getFurniVariableManager().hasVariable(item.getId(), variableId)) {
                        return room.getFurniVariableManager().getCurrentValue(item.getId(), variableId);
                    }
                }
                return null;
            }
        }
    }

    /** Our holder kind (0 user, 1 furni, 2 context) for an official variable target, or -1. */
    public static int holderOf(int officialTarget) {
        return switch (officialTarget) {
            case TARGET_USER -> 0;
            case TARGET_FURNI -> 1;
            case TARGET_CONTEXT -> 2;
            default -> -1;
        };
    }

    /** The official variable target of our holder kind (0 user, 1 furni, 2 context). */
    public static int officialOf(int holder) {
        return switch (holder) {
            case 1 -> TARGET_FURNI;
            case 2 -> TARGET_CONTEXT;
            default -> TARGET_USER;
        };
    }

    /** A variable id from text, or 0. */
    public static int parseId(String text) {
        if (text == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
