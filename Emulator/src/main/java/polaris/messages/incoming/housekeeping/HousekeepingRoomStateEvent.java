package polaris.messages.incoming.housekeeping;

import polaris.Emulator;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomState;
import polaris.messages.outgoing.housekeeping.HousekeepingActionResultComposer;

/**
 * Toggle the room state between OPEN (open) and LOCKED (closed). The
 * client picks which transition it wants via the boolean — true => OPEN,
 * false => LOCKED. Persists state through `Room.save()` so the change
 * outlives an unload.
 */
public class HousekeepingRoomStateEvent extends HousekeepingHandler {
    @Override
    protected String requiredPermission() {
        return HousekeepingAreas.ROOMS;
    }

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
        boolean open = this.packet.readBoolean();
        String actionKey = open ? "room.open" : "room.close";

        if (roomId <= 0) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.invalid_input"));
            return;
        }

        Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(roomId, false);

        if (room == null) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.room_not_found"));
            return;
        }

        if (!HousekeepingRoomGuard.canManageRoom(this.client.getUser(), room)) {
            this.client.sendResponse(
                    new HousekeepingActionResultComposer(actionKey, false, 0, "housekeeping.error.rank_too_high"));
            return;
        }

        room.setState(open ? RoomState.OPEN : RoomState.LOCKED);
        // Room.save() only writes a room flagged as changed, and setState does not flag it.
        room.setNeedsUpdate(true);
        room.save();

        polaris.hotel.modtool.HousekeepingAuditLog.log(
                this.client.getUser().getUserInfo().getId(),
                this.client.getUser().getUserInfo().getUsername(),
                actionKey,
                polaris.hotel.modtool.HousekeepingAuditLog.TARGET_ROOM,
                roomId,
                room.getName(),
                "roomId=" + roomId + " open=" + open,
                this.client.getUser().getUserInfo().getIpLogin());
        this.client.sendResponse(new HousekeepingActionResultComposer(actionKey, true, roomId, ""));
    }
}
