package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingKickAllFromRoomEvent extends MessageHandler {
    private static final String ACTION_KEY = "room.kick_all";

    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        if (!this.client.getHabbo().hasPermission(Permission.ACC_HOUSEKEEPING)) {
            return;
        }

        int roomId = this.packet.readInt();

        if (roomId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.room_not_found"));
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getHabbo(), room)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        room.ejectAll();

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, 0, "roomId=" + roomId,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }
}
