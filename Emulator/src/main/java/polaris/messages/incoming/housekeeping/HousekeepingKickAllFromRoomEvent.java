package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

public class HousekeepingKickAllFromRoomEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

    private static final String ACTION_KEY = "room.kick_all";

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

        if (roomId <= 0) {
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

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(ACTION_KEY, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        room.ejectAll();

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                ACTION_KEY,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId,
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(ACTION_KEY, true, roomId, ""));
    }
}
