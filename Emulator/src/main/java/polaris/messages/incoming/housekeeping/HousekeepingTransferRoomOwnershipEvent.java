package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.UserInfo;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

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

    private static final String ACTION_KEY = "room.transfer";

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

        if (roomId <= 0 || newOwnerId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.room_not_found"));
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)
                || !HousekeepingTargetRankGuard.canTargetUser(this.client.getUser(), newOwnerId)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        UserInfo newOwner = Emulator.getGameEnvironment().getUserManager().getUserInfo(newOwnerId);

        if (newOwner == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(
                    ACTION_KEY, false, 0, "housekeeping.error.new_owner_not_found"));
            return;
        }

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE rooms SET owner_id = ?, owner_name = ? WHERE id = ? LIMIT 1")) {
            statement.setInt(1, newOwnerId);
            statement.setString(2, newOwner.getUsername());
            statement.setInt(3, roomId);
            int rows = statement.executeUpdate();

            if (rows == 0) {
                this.client.sendResponse(new HousekeepingActionResultComposer(
                        ACTION_KEY, false, 0, "housekeeping.error.room_not_found"));
                return;
            }
        } catch (SQLException e) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.db_failed"));
            return;
        }

        room.setOwnerId(newOwnerId);
        room.setOwnerName(newOwner.getUsername());

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId + " newOwner=" + newOwner.getUsername(),
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }
}
