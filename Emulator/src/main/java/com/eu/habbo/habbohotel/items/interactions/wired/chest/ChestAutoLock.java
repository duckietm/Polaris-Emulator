package com.eu.habbo.habbohotel.items.interactions.wired.chest;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * Closes a departing player's chest windows, and locks the chests they own that asked for it.
 *
 * <p>A chest is only as safe as the person watching it. Auto-lock is the switch that says "I do not
 * want this left open when I am not here", so it fires the moment the owner walks out of the room and
 * not a moment earlier — leaving the chest exactly as they would have left it by hand.
 *
 * <p>Closing a window happens for every chest they had open; locking happens only to the ones
 * they own that asked for it.
 */
public final class ChestAutoLock {
    private ChestAutoLock() {}

    /** Lock every auto-lock chest in the room that belongs to the player who is leaving. */
    public static void onOwnerLeftRoom(Room room, Habbo habbo) {
        if (room == null || habbo == null) return;

        int ownerId = habbo.getHabboInfo().getId();

        for (HabboItem item : room.getFloorItems()) {
            if (!(item instanceof InteractionWiredChest chest)) continue;

            // Anyone leaving stops looking into every chest, whoever owns it: a window cannot outlive
            // the room it was opened in, and a lid left open would never close again.
            chest.closeFor(habbo, room);

            if (chest.getUserId() != ownerId) continue;
            if (!chest.getContents().applyAutoLockOnOwnerExit()) continue;

            chest.persistContents(room);
        }
    }
}
