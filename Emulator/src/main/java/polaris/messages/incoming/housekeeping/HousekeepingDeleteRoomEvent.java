package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomDeleter;

/**
 * Permanently deletes a room the same way its owner does from the navigator
 * ({@link RoomDeleter}): furni, bots and pets go back to their owners, the
 * room's group is removed, and rights, votes, word filter and custom model
 * rows go with it. The room is loaded with its data first, otherwise there
 * would be no items to give back.
 */
public class HousekeepingDeleteRoomEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    public static final String ACTION_KEY = "room.delete";

    @Override
    public int getRatelimit() {
        return 2000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.allowed()) {
            return;
        }

        int roomId = this.packet.readInt();

        this.reply(
                ACTION_KEY,
                delete(
                        this.operator(),
                        roomId,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)));
    }

    /**
     * Deletes a room for good, the way its owner does.
     *
     * @param mayManage the operator's rank guard on the room's owner
     */
    public static HousekeepingResult delete(HousekeepingOperator operator, int roomId, Predicate<Room> mayManage) {
        if (roomId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        GameEnvironment environment = Emulator.getGameEnvironment();
        Room room = environment.getRoomManager().loadRoom(roomId, true);

        if (room == null) {
            return HousekeepingResult.fail("housekeeping.error.room_not_found");
        }

        if (!mayManage.test(room)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        String roomName = room.getName();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Pets are saved in place: this is a rare staff action, not a hot path.
            RoomDeleter.delete(room, environment, connection, Runnable::run);
        } catch (SQLException e) {
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                roomName,
                "roomId=" + roomId + " owner=" + room.getOwnerName(),
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
