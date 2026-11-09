package polaris.messages.incoming.housekeeping;

import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.rooms.Room;

public class HousekeepingKickAllFromRoomEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    public static final String ACTION_KEY = "room.kick_all";

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

        this.reply(
                ACTION_KEY,
                kickAll(
                        this.operator(),
                        roomId,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)));
    }

    /**
     * Sends everybody out of a room.
     *
     * @param mayManage the operator's rank guard on the room's owner
     */
    public static HousekeepingResult kickAll(HousekeepingOperator operator, int roomId, Predicate<Room> mayManage) {
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

        room.ejectAll();

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId,
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
