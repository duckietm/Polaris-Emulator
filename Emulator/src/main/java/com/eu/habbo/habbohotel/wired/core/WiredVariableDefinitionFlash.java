package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableEcho;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.Set;

/**
 * A Give Variable, Change Variable Value or Remove Variable that wrote a variable also flashes that variable's
 * definition box and the variable add-ons on its tile (Time Utilities, Level-up and the like), as in Habbo.
 *
 * <p>The definition box shows the kind of the last write: 6 for a give, 2 for a change and 8 for a remove, moving to
 * the pair's other state (7, 3, 9) when the same kind writes again; a change that leaves the value as it was only
 * flips the box between 0 and 1. A write through an echo shows on its source's definition, and the echo box only
 * flips.
 */
public final class WiredVariableDefinitionFlash {
    /** A change that left the value as it was: the box flips between 0 and 1. */
    public static final int KIND_UNCHANGED = 0;

    public static final int KIND_CHANGED = 2;
    public static final int KIND_GIVEN = 6;
    public static final int KIND_REMOVED = 8;

    private static final int MAX_ECHO_DEPTH = 4;

    private WiredVariableDefinitionFlash() {}

    /** Flashes the definition box {@code definitionItemId} and the add-on boxes on its tile, as a change. */
    public static void flash(Room room, int definitionItemId) {
        flash(room, definitionItemId, KIND_CHANGED);
    }

    /** Shows a write of {@code kind} on the definition box {@code definitionItemId} and flashes the add-ons on its tile. */
    public static void flash(Room room, int definitionItemId, int kind) {
        flash(room, definitionItemId, kind, 0);
    }

    private static void flash(Room room, int definitionItemId, int kind, int depth) {
        if (depth > MAX_ECHO_DEPTH || room == null || definitionItemId <= 0 || room.getRoomSpecialTypes() == null)
            return;

        InteractionWiredExtra definition = room.getRoomSpecialTypes().getExtra(definitionItemId);
        if (definition == null) return;

        if (definition instanceof WiredExtraVariableEcho echo
                && echo.getSourceVariableItemId() > 0
                && echo.getSourceVariableItemId() != definitionItemId
                && room.getRoomSpecialTypes().getExtra(echo.getSourceVariableItemId()) != null) {
            definition.flashBox(room);
            flash(room, echo.getSourceVariableItemId(), kind, depth + 1);
            return;
        }

        if (!room.isHideWired() && room.getWiredRuntime() != null) {
            room.getWiredRuntime().getBoxFlashes().flip(definition, current -> nextState(current, kind));
        }

        Set<InteractionWiredExtra> onTile = room.getRoomSpecialTypes().getExtras(definition.getX(), definition.getY());
        if (onTile == null) return;
        for (InteractionWiredExtra extra : onTile) {
            if (extra != null && extra != definition) extra.flashBox(room);
        }
    }

    /** The state a definition box shows after a write of {@code kind}. */
    public static String nextState(String current, int kind) {
        if (kind == KIND_UNCHANGED) {
            return "0".equals(current) ? "1" : "0";
        }
        return Integer.toString(Integer.toString(kind).equals(current) ? kind + 1 : kind);
    }
}
