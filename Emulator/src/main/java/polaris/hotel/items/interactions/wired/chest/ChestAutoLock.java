package polaris.hotel.items.interactions.wired.chest;

import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

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
    public static void onOwnerLeftRoom(Room room, User user) {
        if (room == null || user == null) return;

        int ownerId = user.getUserInfo().getId();

        for (UserItem item : room.getFloorItems()) {
            if (!(item instanceof InteractionWiredChest chest)) continue;

            // Anyone leaving stops looking into every chest, whoever owns it: a window cannot outlive
            // the room it was opened in, and a lid left open would never close again.
            chest.closeFor(user, room);

            if (chest.getUserId() != ownerId) continue;
            if (!chest.getContents().applyAutoLockOnOwnerExit()) continue;

            // The lock lives in the furni data the client reads, so the room is told even when the
            // lid does not move.
            chest.persistContents();
            chest.publishSettings(room);
        }
    }
}
