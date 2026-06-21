package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomState;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Toggle the room state between OPEN (open) and LOCKED (closed). The
 * client picks which transition it wants via the boolean — true => OPEN,
 * false => LOCKED. Persists state through `Room.save()` so the change
 * outlives an unload.
 */
public class HousekeepingRoomStateEvent extends MessageHandler {
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
        boolean open = this.packet.readBoolean();
        String actionKey = open ? "room.open" : "room.close";

        if (roomId <= 0) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.room_not_found"));
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getHabbo(), room)) {
            this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        room.setState(open ? RoomState.OPEN : RoomState.LOCKED);
        room.save();

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                actionKey, 0, "roomId=" + roomId + " open=" + open,
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, roomId, ""));
    }
}
