package polaris.messages.incoming.housekeeping;

import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.rooms.Room;

/**
 * Toggle room-wide mute. Habbo's Room.setMuted is boolean, not duration-
 * scoped, so the wire `minutes` arg picks the semantic: minutes==0 =>
 * unmute, minutes>0 => mute. An emulator-side scheduled unmute could
 * use the value as a timer, but for now the mute stays until the
 * operator unmutes manually — the minutes is reserved as a forward-
 * compat field on the wire.
 */
public class HousekeepingMuteRoomEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    public static final String ACTION_KEY = "room.mute";

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
        int minutes = this.packet.readInt();

        this.reply(
                ACTION_KEY,
                mute(
                        this.operator(),
                        roomId,
                        minutes,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)));
    }

    /**
     * Mutes a room (minutes &gt; 0) or unmutes it (0).
     *
     * @param mayManage the operator's rank guard on the room's owner
     */
    public static HousekeepingResult mute(
            HousekeepingOperator operator, int roomId, int minutes, Predicate<Room> mayManage) {
        if (roomId <= 0 || minutes < 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            return HousekeepingResult.fail("housekeeping.error.room_not_found");
        }

        if (!mayManage.test(room)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        room.setMuted(minutes > 0);

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId + " minutes=" + minutes + " muted=" + (minutes > 0),
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
