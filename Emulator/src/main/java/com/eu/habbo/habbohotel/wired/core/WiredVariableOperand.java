package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.UserVariableHolders;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import java.util.Collection;

/**
 * A number read from a variable, for boxes whose value can be typed or taken from a variable (the
 * editor's "set value / from variable" section): which kind of variable, which one, and whose.
 */
public final class WiredVariableOperand {
    public static final int TARGET_USER = 0;
    public static final int TARGET_FURNI = 1;
    public static final int TARGET_CONTEXT = 2;
    public static final int TARGET_ROOM = 3;
    /** The furni picked in the box itself, as the reference's source. */
    public static final int SOURCE_SECONDARY_SELECTED = 101;

    private static final String CUSTOM_TOKEN_PREFIX = "custom:";
    private static final String INTERNAL_TOKEN_PREFIX = "internal:";

    private WiredVariableOperand() {}

    public static int normalizeTarget(int value) {
        return switch (value) {
            case TARGET_FURNI, TARGET_CONTEXT, TARGET_ROOM -> value;
            default -> TARGET_USER;
        };
    }

    public static int normalizeUserSource(int value) {
        return WiredSourceUtil.isDefaultUserSource(value) ? value : WiredSourceUtil.SOURCE_TRIGGER;
    }

    public static int normalizeFurniSource(int value) {
        return switch (value) {
            case SOURCE_SECONDARY_SELECTED, WiredSourceUtil.SOURCE_SELECTOR, WiredSourceUtil.SOURCE_SIGNAL -> value;
            default -> WiredSourceUtil.SOURCE_TRIGGER;
        };
    }

    /** "custom:&lt;id&gt;", "internal:&lt;key&gt;", or a bare id; anything else is no variable. */
    public static String normalizeToken(String token) {
        if (token == null || token.isBlank()) return "";

        String normalized = token.trim();
        if (normalized.startsWith(INTERNAL_TOKEN_PREFIX)) {
            return INTERNAL_TOKEN_PREFIX
                    + WiredInternalVariableSupport.normalizeKey(normalized.substring(INTERNAL_TOKEN_PREFIX.length()));
        }
        if (normalized.startsWith(CUSTOM_TOKEN_PREFIX)) {
            int id = customItemId(normalized);
            return (id > 0) ? CUSTOM_TOKEN_PREFIX + id : "";
        }

        try {
            int parsed = Integer.parseInt(normalized);
            return (parsed > 0) ? CUSTOM_TOKEN_PREFIX + parsed : "";
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /**
     * The value of the variable for the first holder of the given source that has it, or null
     * when nobody does.
     */
    public static Integer read(
            WiredContext ctx,
            Room room,
            int target,
            String token,
            int userSource,
            int furniSource,
            Collection<HabboItem> pickedFurni) {
        if (room == null || token == null || token.isEmpty()) return null;

        String internalKey = token.startsWith(INTERNAL_TOKEN_PREFIX)
                ? WiredInternalVariableSupport.normalizeKey(token.substring(INTERNAL_TOKEN_PREFIX.length()))
                : null;
        int itemId = customItemId(token);

        switch (target) {
            case TARGET_FURNI -> {
                int source = (furniSource == SOURCE_SECONDARY_SELECTED) ? WiredSourceUtil.SOURCE_SELECTED : furniSource;
                for (HabboItem item : WiredSourceUtil.resolveItems(ctx, source, pickedFurni)) {
                    Integer value = readFurni(room, item, internalKey, itemId);
                    if (value != null) return value;
                }
                return null;
            }
            case TARGET_ROOM -> {
                if (internalKey != null) {
                    return WiredInternalVariableSupport.canUseRoomReference(internalKey)
                            ? WiredInternalVariableSupport.readRoomValue(room, internalKey)
                            : null;
                }
                WiredVariableDefinitionInfo definition =
                        room.getRoomVariableManager().getDefinitionInfo(itemId);
                return (definition != null && definition.hasValue())
                        ? room.getRoomVariableManager().getCurrentValue(itemId)
                        : null;
            }
            case TARGET_CONTEXT -> {
                if (ctx == null) return null;
                if (internalKey != null) {
                    return WiredInternalVariableSupport.canUseContextReference(internalKey)
                            ? WiredInternalVariableSupport.readContextValue(ctx, internalKey)
                            : null;
                }
                WiredVariableDefinitionInfo definition = WiredContextVariableSupport.getDefinitionInfo(room, itemId);
                return (definition != null
                                && definition.hasValue()
                                && WiredContextVariableSupport.hasVariable(ctx, itemId))
                        ? WiredContextVariableSupport.getCurrentValue(ctx, itemId)
                        : null;
            }
            default -> {
                for (RoomUnit unit : WiredSourceUtil.resolveUsers(ctx, userSource)) {
                    Integer value = readUser(room, unit, internalKey, itemId);
                    if (value != null) return value;
                }
                return null;
            }
        }
    }

    private static Integer readUser(Room room, RoomUnit unit, String internalKey, int itemId) {
        if (unit == null) return null;
        if (internalKey != null) {
            return WiredInternalVariableSupport.canUseUserReference(internalKey)
                    ? WiredInternalVariableSupport.readUserValue(room, unit, internalKey)
                    : null;
        }

        WiredVariableDefinitionInfo definition = room.getUserVariableManager().getDefinitionInfo(itemId);
        int userId = UserVariableHolders.of(room, unit);
        if (definition == null || !definition.hasValue() || userId == 0) return null;

        return room.getUserVariableManager().hasVariable(userId, itemId)
                ? room.getUserVariableManager().getCurrentValue(userId, itemId)
                : null;
    }

    private static Integer readFurni(Room room, HabboItem item, String internalKey, int itemId) {
        if (item == null) return null;
        if (internalKey != null) {
            return WiredInternalVariableSupport.canUseFurniReference(internalKey)
                    ? WiredInternalVariableSupport.readFurniValue(room, item, internalKey)
                    : null;
        }

        WiredVariableDefinitionInfo definition = room.getFurniVariableManager().getDefinitionInfo(itemId);
        if (definition == null || !definition.hasValue()) return null;

        return room.getFurniVariableManager().hasVariable(item.getId(), itemId)
                ? room.getFurniVariableManager().getCurrentValue(item.getId(), itemId)
                : null;
    }

    private static int customItemId(String token) {
        if (token == null || !token.startsWith(CUSTOM_TOKEN_PREFIX)) return 0;

        try {
            return Integer.parseInt(token.substring(CUSTOM_TOKEN_PREFIX.length()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
