package polaris.messages.incoming.rooms.items;

import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;

/**
 * Locks or unlocks the room's wired chests from the chests tab. Reads {@code bool lock, bool all}.
 *
 * <p>{@code all = false} touches only the chests this player owns, which needs room rights.
 * {@code all = true} touches every chest in the room and is reserved for the room owner (or staff
 * holding {@link Permission#ACC_ANYROOMOWNER}) — it can freeze furni belonging to other people.
 *
 * <p>A locked chest keeps answering wired effects but refuses everything a player does by hand,
 * deposits included. That is the point of the switch: freeze a chest without breaking the room that
 * runs on it.
 */
public class WiredChestLockEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        Room room = user.getUserInfo().getCurrentRoom();
        if (room == null) return;

        boolean lock = this.packet.readBoolean();
        boolean all = this.packet.readBoolean();

        if (all) {
            if (!room.isOwner(user) && !user.hasPermission(Permission.ACC_ANYROOMOWNER)) return;
        } else if (!room.hasRights(user)) {
            return;
        }

        int ownerId = user.getUserInfo().getId();

        // Enumerated over the floor items rather than RoomSpecialTypes: that index matches on the
        // exact class, and every chest here is one of the concrete subclasses.
        for (UserItem item : room.getFloorItems()) {
            if (!(item instanceof InteractionWiredChest chest)) continue;
            if (!all && chest.getUserId() != ownerId) continue;
            // Others may lock a chest, but only its owner ever unlocks it.
            if (!lock && chest.getUserId() != ownerId) continue;
            if (chest.getContents().isLocked() == lock) continue;

            chest.getContents().setLocked(lock);
            chest.persistContents();
            chest.publishSettings(room);
        }
        // AIR expects no reply: every chest's new state reaches the room with its furni.
    }
}
