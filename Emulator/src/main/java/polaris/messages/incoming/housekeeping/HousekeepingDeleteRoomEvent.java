package polaris.messages.incoming.housekeeping;

import java.sql.Connection;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.GameEnvironment;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomDeleter;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

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

    private static final String ACTION_KEY = "room.delete";

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

        if (roomId <= 0) {
            this.fail("housekeeping.error.invalid_input");
            return;
        }

        GameEnvironment environment = Emulator.getGameEnvironment();
        Room room = environment.getRoomManager().loadRoom(roomId, true);

        if (room == null) {
            this.fail("housekeeping.error.room_not_found");
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)) {
            this.fail("housekeeping.error.rank_too_high");
            return;
        }

        String roomName = room.getName();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            // Pets are saved in place: this is a rare staff action, not a hot path.
            RoomDeleter.delete(room, environment, connection, Runnable::run);
        } catch (SQLException e) {
            this.fail("housekeeping.error.db_failed");
            return;
        }

        HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                roomName,
                "roomId=" + roomId + " owner=" + room.getOwnerName(),
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }

    private void fail(String message) {
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, message));
    }
}
