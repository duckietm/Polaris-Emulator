package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraContextVariable;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraPointerVariable;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.WiredVariableDefinitionInfo;
import com.eu.habbo.habbohotel.wired.protocol.WiredVariableTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The indirection behind the official pointer variable ({@link WiredExtraPointerVariable}). A pointer is
 * listed as a variable of its source type, so editors can pick it, but it never holds a value itself:
 * every box that uses a variable resolves it here first, with the running stack's context. The
 * pointer's accessor context variable then holds the furni id of the WIRED box of the variable it
 * stands for, which must be a variable of the pointer's source type. Without a stack, or when the
 * accessor is missing or names anything else, the pointer resolves to no variable and the box does
 * nothing, as a box without a chosen variable does.
 */
public final class WiredPointerVariableSupport {
    /** Official error when a pointer is picked where no stack runs (the variable-changed trigger, echo, fx, ...). */
    public static final String REQUIRES_EXECUTION_CONTEXT_ERROR =
            "wiredfurni.error.variables.requires_execution_context";

    private WiredPointerVariableSupport() {}

    /** The pointer box with this id, or null. */
    public static WiredExtraPointerVariable pointer(Room room, int itemId) {
        if (room == null || room.getRoomSpecialTypes() == null || itemId <= 0) return null;

        InteractionWiredExtra extra = room.getRoomSpecialTypes().getExtra(itemId);
        return extra instanceof WiredExtraPointerVariable pointer ? pointer : null;
    }

    public static boolean isPointer(Room room, int itemId) {
        return pointer(room, itemId) != null;
    }

    /**
     * Whether a box without a delay is pointed at a pointer: Habbo then writes nothing through it and reads no value
     * from it (observed on AIR); a delayed box resolves the pointer when it runs.
     */
    public static boolean ignoredWithoutDelay(Room room, int delay, int itemId) {
        return delay <= 0 && itemId > 0 && isPointer(room, itemId);
    }

    /** The room's named pointers listed under an official variable target, by name and then id. */
    public static List<WiredExtraPointerVariable> pointers(Room room, int officialTarget) {
        List<WiredExtraPointerVariable> result = new ArrayList<>();
        if (room == null || room.getRoomSpecialTypes() == null) return result;

        for (InteractionWiredExtra extra : room.getRoomSpecialTypes().getExtras()) {
            if (extra instanceof WiredExtraPointerVariable pointer
                    && pointer.getSourceType() == officialTarget
                    && hasName(pointer)) {
                result.add(pointer);
            }
        }

        result.sort(Comparator.comparing(WiredExtraPointerVariable::getVariableName, String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(WiredExtraPointerVariable::getId));
        return result;
    }

    /** Whether any of these ids is a pointer (for boxes that read variables without a running stack). */
    public static boolean anyPointer(Room room, int... itemIds) {
        for (int itemId : itemIds) {
            if (isPointer(room, itemId)) return true;
        }
        return false;
    }

    /** How a pointer is listed: a writable variable with a value that needs a running stack. */
    public static WiredVariableDefinitionInfo definitionInfo(WiredExtraPointerVariable pointer) {
        return new WiredVariableDefinitionInfo(
                pointer.getId(), pointer.getVariableName(), true, 0, false, false, false, true);
    }

    /** The listing of the pointer with this id under an official target, or null when it is no such pointer. */
    public static WiredVariableDefinitionInfo definitionInfo(Room room, int itemId, int officialTarget) {
        WiredExtraPointerVariable pointer = pointer(room, itemId);
        return pointer != null && pointer.getSourceType() == officialTarget && hasName(pointer)
                ? definitionInfo(pointer)
                : null;
    }

    /** The listings of the room's pointers under an official target. */
    public static List<WiredVariableDefinitionInfo> definitionInfos(Room room, int officialTarget) {
        List<WiredVariableDefinitionInfo> result = new ArrayList<>();
        for (WiredExtraPointerVariable pointer : pointers(room, officialTarget)) {
            result.add(definitionInfo(pointer));
        }
        return result;
    }

    /**
     * AIR's accessor filter: a user-defined context variable with a value that needs no running stack
     * (so neither a pointer nor an array).
     */
    public static boolean isValidAccessor(Room room, int itemId) {
        WiredExtraContextVariable definition = WiredContextVariableSupport.getDefinition(room, itemId);
        return definition != null
                && definition.hasValue()
                && !definition.isArray()
                && hasText(definition.getVariableName());
    }

    /**
     * The variable a box should use for {@code variableItemId} in this stack: the id itself when it is no
     * pointer, else the variable the pointer stands for, or 0 when it stands for nothing right now.
     */
    public static int resolve(WiredContext ctx, Room room, int variableItemId) {
        WiredExtraPointerVariable pointer = pointer(room, variableItemId);
        if (pointer == null) return variableItemId;
        if (ctx == null) return 0;

        int accessor = pointer.getAccessorVariableId();
        if (!isValidAccessor(room, accessor) || !WiredContextVariableSupport.hasVariable(ctx, accessor)) return 0;

        Integer held = WiredContextVariableSupport.getCurrentValue(ctx, accessor);
        // The accessor may hold the id the client knows a Builders Club box by (its @id).
        Integer target = held == null ? null : BuildersClubItemIds.fromWire(room, held);
        if (target == null || target <= 0 || target == pointer.getId() || isPointer(room, target)) return 0;

        return isVariableOf(room, pointer.getSourceType(), target) ? target : 0;
    }

    /** Whether the box with this id defines a variable listed under the official target. */
    static boolean isVariableOf(Room room, int officialTarget, int itemId) {
        return switch (officialTarget) {
            case WiredVariableTargets.OFFICIAL_USER ->
                room.getUserVariableManager().getDefinitionInfo(itemId) != null;
            case WiredVariableTargets.OFFICIAL_GLOBAL ->
                room.getRoomVariableManager().getDefinitionInfo(itemId) != null;
            case WiredVariableTargets.OFFICIAL_CONTEXT ->
                WiredContextVariableSupport.getDefinitionInfo(room, itemId) != null;
            default -> room.getFurniVariableManager().getDefinitionInfo(itemId) != null;
        };
    }

    private static boolean hasName(WiredExtraPointerVariable pointer) {
        return hasText(pointer.getVariableName());
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
