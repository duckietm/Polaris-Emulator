package com.eu.habbo.messages.incoming.housekeeping;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.permissions.Permission;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Toggle room-wide mute. Habbo's Room.setMuted is boolean, not duration-
 * scoped, so the wire `minutes` arg picks the semantic: minutes==0 =>
 * unmute, minutes>0 => mute. An emulator-side scheduled unmute could
 * use the value as a timer, but for now the mute stays until the
 * operator unmutes manually — the minutes is reserved as a forward-
 * compat field on the wire.
 */
public class HousekeepingMuteRoomEvent extends MessageHandler {
    private static final String ACTION_KEY = "room.mute";

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
        int minutes = this.packet.readInt();

        if (roomId <= 0 || minutes < 0) {
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

        room.setMuted(minutes > 0);

        com.eu.habbo.habbohotel.modtool.HousekeepingAuditLog.log(
                this.client.getHabbo().getHabboInfo().getId(),
                this.client.getHabbo().getHabboInfo().getUsername(),
                ACTION_KEY, 0, "roomId=" + roomId + " minutes=" + minutes + " muted=" + (minutes > 0),
                this.client.getHabbo().getHabboInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }
}
