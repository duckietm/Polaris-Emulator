package com.eu.habbo.messages.incoming.wired;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomTileState;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.rooms.RoomUserRotation;
import com.eu.habbo.habbohotel.wired.core.WiredUserMovementHelper;
import com.eu.habbo.messages.incoming.MessageHandler;

public class WiredUserInspectMoveEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();

        if (room == null) {
            return;
        }

        if (!room.canModifyWired(this.client.getHabbo())) {
            return;
        }

        if (this.packet.bytesAvailable() < 16) {
            return;
        }

        int roomUnitId = this.packet.readInt();
        int x = this.packet.readInt();
        int y = this.packet.readInt();
        int direction = this.packet.readInt();

        RoomUnit roomUnit = resolveRoomUnit(room, roomUnitId);

        if (!WiredUserInspectMovePolicy.canMove(room, this.client.getHabbo(), roomUnit)
                || roomUnit.getCurrentLocation() == null
                || room.getLayout() == null) {
            return;
        }

        RoomUserRotation targetRotation = RoomUserRotation.fromValue((((direction % 8) + 8) % 8));
        boolean positionChanged = roomUnit.getX() != x || roomUnit.getY() != y;
        boolean directionChanged =
                roomUnit.getBodyRotation() != targetRotation || roomUnit.getHeadRotation() != targetRotation;

        if (!positionChanged) {
            if (directionChanged) {
                WiredUserMovementHelper.updateUserDirection(room, roomUnit, targetRotation, targetRotation);
            }

            return;
        }

        RoomTile targetTile = room.getLayout().getTile((short) x, (short) y);

        if (targetTile == null
                || targetTile.state == RoomTileState.INVALID
                || targetTile.state == RoomTileState.BLOCKED) {
            return;
        }

        double targetZ = targetTile.getStackHeight() + ((targetTile.state == RoomTileState.SIT) ? -0.5 : 0);

        if (!WiredUserMovementHelper.moveUser(
                        room,
                        roomUnit,
                        targetTile,
                        targetZ,
                        targetRotation,
                        targetRotation,
                        WiredUserMovementHelper.DEFAULT_ANIMATION_DURATION,
                        false)
                && directionChanged) {
            WiredUserMovementHelper.updateUserDirection(room, roomUnit, targetRotation, targetRotation);
        }
    }

    @Override
    public int getRatelimit() {
        return 100;
    }

    private RoomUnit resolveRoomUnit(Room room, int roomUnitId) {
        if (room == null || roomUnitId <= 0) {
            return null;
        }

        for (RoomUnit roomUnit : room.getRoomUnits()) {
            if (roomUnit != null && roomUnit.getId() == roomUnitId) {
                return roomUnit;
            }
        }

        return null;
    }
}
