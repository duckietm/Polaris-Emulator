package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;

public class RotateMoveItemEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null) return;

        int furniId = this.packet.readInt();
        if (!RoomItemInputGuard.isPositiveId(furniId)) return;

        HabboItem item = room.getHabboItem(furniId);
        if (item == null) return;

        int x = this.packet.readInt();
        int y = this.packet.readInt();
        int rotation = this.packet.readInt();

        RoomTile tile = room.getLayout().getTile((short) x, (short) y);
        if (tile == null) return;

        FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getHabbo(), tile, rotation);
        if (error != FurnitureMovementError.NONE) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
            return;
        }

        error = room.moveFurniTo(item, tile, rotation, this.client.getHabbo());
        if (error != FurnitureMovementError.NONE) {
            this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
        }
    }
}
