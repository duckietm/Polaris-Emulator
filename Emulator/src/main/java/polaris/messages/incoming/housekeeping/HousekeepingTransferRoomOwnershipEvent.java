package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.function.Predicate;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserInfo;

/**
 * Transfer ownership of a room to a different user. Updates both
 * `rooms.owner_id` and `rooms.owner_name` so the cached owner name on
 * the navigator stays in sync without forcing a relog. The room is
 * touched via direct SQL rather than via Room.setOwnerId() because
 * the room may not be loaded.
 */
public class HousekeepingTransferRoomOwnershipEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    public static final String ACTION_KEY = "room.transfer";

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
        int newOwnerId = this.packet.readInt();

        this.reply(
                ACTION_KEY,
                transfer(
                        this.operator(),
                        roomId,
                        newOwnerId,
                        room -> HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)
                                && HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), newOwnerId)));
    }

    /**
     * Gives a room to another user.
     *
     * @param mayManage the operator's rank guard on the room's owner and on the new owner
     */
    public static HousekeepingResult transfer(
            HousekeepingOperator operator, int roomId, int newOwnerId, Predicate<Room> mayManage) {
        if (roomId <= 0 || newOwnerId <= 0) {
            return HousekeepingResult.fail("housekeeping.error.invalid_input");
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            return HousekeepingResult.fail("housekeeping.error.room_not_found");
        }

        if (!mayManage.test(room)) {
            return HousekeepingResult.fail("housekeeping.error.rank_too_high");
        }

        UserInfo newOwner = Emulator.getGameEnvironment().getUserManager().getUserInfo(newOwnerId);

        if (newOwner == null) {
            return HousekeepingResult.fail("housekeeping.error.new_owner_not_found");
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rooms SET owner_id = ?, owner_name = ? WHERE id = ? LIMIT 1")) {
            statement.setInt(1, newOwnerId);
            statement.setString(2, newOwner.getUsername());
            statement.setInt(3, roomId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                return HousekeepingResult.fail("housekeeping.error.room_not_found");
            }
        } catch (SQLException e) {
            return HousekeepingResult.fail("housekeeping.error.db_failed");
        }

        room.setOwnerId(newOwnerId);
        room.setOwnerName(newOwner.getUsername());

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                operator.id(),
                operator.name(),
                ACTION_KEY,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId + " newOwner=" + newOwner.getUsername(),
                operator.ip());
        return HousekeepingResult.ok(roomId);
    }
}
