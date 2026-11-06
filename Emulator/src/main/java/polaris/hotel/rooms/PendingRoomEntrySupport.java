package polaris.hotel.rooms;

import polaris.hotel.items.interactions.InteractionTeleport;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;

/** Applies a {@link PendingRoomEntry} when the user it was told for opens that room. */
final class PendingRoomEntrySupport {

    private PendingRoomEntrySupport() {}

    /**
     * Reads the pending arrival once. It only shapes an entry through the door: a furniture
     * teleport or a reconnect already placed the user and keeps its own method.
     */
    static void apply(User user, Room room, boolean enteringAtDoor, long now) {
        UserInfo info = user.getUserInfo();
        PendingRoomEntry entry = info.takePendingRoomEntry(room.getId(), now);
        if (entry == null || !enteringAtDoor) {
            return;
        }

        if (PendingRoomEntry.METHOD_ROOM_NETWORK.equals(entry.method())) {
            info.setRoomEntryMethod(PendingRoomEntry.METHOD_ROOM_NETWORK);
            info.setRoomEntryTeleportId(0);
            return;
        }

        if (!PendingRoomEntry.METHOD_TELEPORT.equals(entry.method())) {
            return;
        }

        RoomUnit unit = user.getRoomUnit();
        UserItem teleport = room.getUserItem(entry.teleportItemId());
        if (unit == null || !(teleport instanceof InteractionTeleport) || room.getLayout() == null) {
            return;
        }

        RoomTile tile = room.getLayout().getTile(teleport.getX(), teleport.getY());
        if (tile == null) {
            return;
        }

        unit.setLocation(tile);
        unit.setZ(tile.getStackHeight());
        unit.setPreviousLocationZ(tile.getStackHeight());
        RoomUserRotation rotation = RoomUserRotation.values()[Math.floorMod(teleport.getRotation(), 8)];
        unit.setBodyRotation(rotation);
        unit.setHeadRotation(rotation);
        info.setRoomEntryMethod(PendingRoomEntry.METHOD_TELEPORT);
        info.setRoomEntryTeleportId(teleport.getId());
    }
}
