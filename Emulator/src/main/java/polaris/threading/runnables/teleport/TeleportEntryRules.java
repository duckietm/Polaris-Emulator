package polaris.threading.runnables.teleport;

import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

/** Whether a teleport (a door, a tile or an arrow) may take a user into another room. */
public final class TeleportEntryRules {

    private TeleportEntryRules() {}

    /** Not banned there (unless they may enter any room), and the room has space or they may enter it full. */
    public static boolean mayEnter(User user, Room room) {
        boolean anyRoom =
                user.hasPermission(Permission.ACC_ANYROOMOWNER) || user.hasPermission(Permission.ACC_ENTERANYROOM);

        if (room.isBanned(user) && !anyRoom) {
            return false;
        }

        return room.getUserCount() < room.getUsersMax()
                || user.hasPermission(Permission.ACC_FULLROOMS)
                || room.hasRights(user);
    }
}
