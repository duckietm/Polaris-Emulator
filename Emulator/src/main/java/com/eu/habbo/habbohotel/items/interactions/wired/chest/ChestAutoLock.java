package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * Closes a departing owner's chests behind them.
 *
 * <p>A chest is only as safe as the person watching it. Auto-lock is the switch that says "I do not
 * want this left open when I am not here", so it fires the moment the owner walks out of the room and
 * not a moment earlier — leaving the chest exactly as they would have left it by hand.
 *
 * <p>Only chests the leaver owns are touched, and only the ones that asked for this.
 */
public final class ChestAutoLock {
    private ChestAutoLock() {}

    /** Lock every auto-lock chest in the room that belongs to the player who is leaving. */
    public static void onOwnerLeftRoom(Room room, Habbo habbo) {
        if (room == null || habbo == null) return;

        int ownerId = habbo.getHabboInfo().getId();

        for (HabboItem item : room.getFloorItems()) {
            if (!(item instanceof InteractionWiredChest chest)) continue;
            if (chest.getUserId() != ownerId) continue;
            if (!chest.getContents().applyAutoLockOnOwnerExit()) continue;

            chest.persistContents();
        }
    }
}
