package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredExtra;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredExtraVariableFx;
import com.eu.habbo.habbohotel.rooms.Room;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * How many variable fx boxes a room draws ({@code wired.variablefx.max.boxes}, 25 by default as on turbo, whose
 * {@code VariableFxMaxBoxes} is the only number known for it). The boxes past it stay in the room, keep their
 * settings and are left out of what the players are shown, together with fx imported from other rooms once the room's
 * own boxes fill the cap. Habbo's monitor counts that as VARIABLE_FX_LIMIT (the error list row that shares id 8 with
 * the placement failure).
 */
public final class WiredVariableFxLimit {

    public static final int DEFAULT_MAX_BOXES = 25;

    private static final AtomicInteger MAX_BOXES = new AtomicInteger(DEFAULT_MAX_BOXES);

    private WiredVariableFxLimit() {}

    public static int maxBoxes() {
        return MAX_BOXES.get();
    }

    /** Sets the cap; a negative cap counts as 0. */
    public static void configure(int maxBoxes) {
        MAX_BOXES.set(Math.max(0, maxBoxes));
    }

    /**
     * How many of the room's own fx boxes are left out now, counting VARIABLE_FX_LIMIT in the room's diagnostics when
     * more are left out than {@code leftOutBefore} (what the caller saw at its last look), so one box over the cap is
     * counted once and not on every flush.
     */
    public static int noteLeftOut(Room room, int leftOutBefore) {
        if (room == null || room.getRoomSpecialTypes() == null) return 0;

        return noteLeftOut(room.getId(), room.getRoomSpecialTypes().getExtras(), leftOutBefore);
    }

    /** {@link #noteLeftOut(Room, int)} over the given extras of the room. */
    static int noteLeftOut(int roomId, Collection<? extends InteractionWiredExtra> extras, int leftOutBefore) {
        int max = MAX_BOXES.get();
        int boxes = 0;
        int firstLeftOutId = 0;

        for (InteractionWiredExtra extra : extras == null ? List.<InteractionWiredExtra>of() : extras) {
            if (!(extra instanceof WiredExtraVariableFx)) continue;

            boxes++;
            if (boxes == max + 1) firstLeftOutId = extra.getId();
        }

        int leftOut = Math.max(0, boxes - max);
        if (leftOut > leftOutBefore) {
            WiredManager.noteVariableFxLimit(
                    roomId,
                    "The room holds " + boxes + " variable fx boxes; only " + max + " are shown",
                    "variable_fx",
                    firstLeftOutId);
        }
        return leftOut;
    }
}
