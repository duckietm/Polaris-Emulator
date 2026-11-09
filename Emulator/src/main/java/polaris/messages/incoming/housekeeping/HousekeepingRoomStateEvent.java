package polaris.messages.incoming.housekeeping;

import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomState;

/**
 * Toggle the room state between OPEN (open) and LOCKED (closed). The
 * client picks which transition it wants via the boolean — true => OPEN,
 * false => LOCKED. Persists state through `Room.save()` so the change
 * outlives an unload.
 */
public class HousekeepingRoomStateEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int roomId = this.packet.readInt();
        boolean open = this.packet.readBoolean();

        this.reply(
                actionKey(open),
                setState(
                        this.operator(),
                        roomId,
                        open,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)));
    }

    public static String actionKey(boolean open) {
        return open ? "room.open" : "room.close";
    }

    /**
     * Opens or locks a room, loaded or not, and stores it.
     *
     * @param mayManage the operator's rank guard on the room's owner
     */
    public static HousekeepingResult setState(
            HousekeepingOperator operator, int roomId, boolean open, Predicate<Room> mayManage) {
        String actionKey = actionKey(open);

        if (roomId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            return HousekeepingResult.fail("housekeeping.error.room_not_found");
        }

        if (!mayManage.test(room)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        room.setState(open ? RoomState.OPEN : RoomState.LOCKED);
        // Room.save() only writes a room flagged as changed, and setState does not flag it.
        room.setNeedsUpdate(true);
        room.save();

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                actionKey,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId + " open=" + open,
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
