package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomDeleter;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;
import java.sql.Connection;
import java.sql.SQLException;

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

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getHabbo(), room)) {
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
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY,
                HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                roomName,
                "roomId=" + roomId + " owner=" + room.getOwnerName(),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }

    private void fail(String message) {
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, message));
    }
}
