package com.eu.habbo.messages.incoming.rooms.items;

import com.eu.habbo.habbohotel.items.FurnitureDirections;
import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.habbohotel.rooms.FurnitureMovementError;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomPlayTestSupport;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertKeys;
import com.eu.habbo.messages.outgoing.rooms.WiredMovementsComposer;
import com.eu.habbo.messages.outgoing.rooms.items.FloorItemUpdateComposer;
import java.util.ArrayList;
import java.util.List;

public class UpdateFurniturePositionEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 100;
    }

    @Override
    public void handle() throws Exception {
        Room room = currentRoom();
        if (room == null) return;

        int furniId = BuildersClubItemIds.fromWire(this.currentRoom(), this.packet.readInt());
        if (!RoomItemInputGuard.isPositiveId(furniId)) return;

        HabboItem item = room.getHabboItem(furniId);
        if (item == null) return;

        // AIR playtest mode: the user moves and rotates furni as a visitor without rights.
        if (RoomPlayTestSupport.isRestricted(this.client.getHabbo())) {
            this.client.sendResponse(new BubbleAlertComposer(
                    BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, FurnitureMovementError.NO_RIGHTS.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
            return;
        }

        int x = this.packet.readInt();
        int y = this.packet.readInt();
        double z = this.packet.readInt() / 10000.0;
        // A direction the furni's asset does not list is stored as its default one, as in Habbo.
        int rotation = FurnitureDirections.of(item).place(this.packet.readInt());

        RoomTile tile = room.getLayout().getTile((short) x, (short) y);
        if (tile == null) return;

        FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getHabbo(), tile, rotation);
        if (error != FurnitureMovementError.NONE) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
            return;
        }

        RoomTile oldTile = room.getLayout().getTile(item.getX(), item.getY());
        double oldZ = item.getZ();

        error = room.moveFurniTo(item, tile, rotation, z, this.client.getHabbo(), false, true);
        if (error != FurnitureMovementError.NONE) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
            return;
        }

        if (oldTile != null) {
            List<WiredMovementsComposer.MovementData> movements = new ArrayList<>(1);
            movements.add(WiredMovementsComposer.furniMovement(
                    item.getId(),
                    oldTile.x,
                    oldTile.y,
                    tile.x,
                    tile.y,
                    oldZ,
                    item.getZ(),
                    item.getRotation(),
                    WiredMovementsComposer.DEFAULT_DURATION));

            room.sendComposer(new WiredMovementsComposer(movements).compose());
        }
    }
}
