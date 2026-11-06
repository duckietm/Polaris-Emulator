package polaris.hotel.wired;

import polaris.hotel.items.interactions.wired.extra.WiredExtraFurniVariable;
import polaris.hotel.items.interactions.wired.extra.WiredExtraUserVariable;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;

/**
 * Who may take a variable away from every holder at once. Unlike the other management actions this
 * one reaches users who are not in the room, so it is reserved for the owner of the definition box,
 * the way the official client reserves it, plus the staff permission that owns every room.
 */
public final class WiredVariableClearPolicy {

    private WiredVariableClearPolicy() {}

    public static boolean canClear(Room room, User user, int definitionItemId) {
        if (room == null || user == null || user.getUserInfo() == null) return false;
        if (!room.canModifyWired(user)) return false;

        UserItem definition = room.getUserItem(definitionItemId);
        if (!(definition instanceof WiredExtraUserVariable) && !(definition instanceof WiredExtraFurniVariable)) {
            return false;
        }

        return definition.getUserId() == user.getUserInfo().getId() || user.hasPermission(Permission.ACC_ANYROOMOWNER);
    }
}
