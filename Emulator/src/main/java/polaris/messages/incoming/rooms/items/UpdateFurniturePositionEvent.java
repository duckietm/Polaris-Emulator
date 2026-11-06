package polaris.messages.incoming.rooms.items;

import java.util.ArrayList;
import java.util.List;
import polaris.hotel.items.FurnitureDirections;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.rooms.BuildersClubItemIds;
import polaris.hotel.rooms.FurnitureMovementError;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomPlayTestSupport;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.users.UserItem;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.rooms.WiredMovementsComposer;
import polaris.messages.outgoing.rooms.items.FloorItemUpdateComposer;

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

        UserItem item = room.getUserItem(furniId);
        // A wall furni has no tile to move to: it moves along the wall (MoveWallItemEvent).
        if (item == null || item.getBaseItem() == null || item.getBaseItem().getType() != FurnitureType.FLOOR) return;

        // AIR playtest mode: the user moves and rotates furni as a visitor without rights.
        if (RoomPlayTestSupport.isRestricted(this.client.getUser())) {
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

        FurnitureMovementError error = room.canPlaceFurnitureAt(item, this.client.getUser(), tile, rotation);
        if (error != FurnitureMovementError.NONE) {
            this.client.sendResponse(
                    new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, error.errorCode));
            this.client.sendResponse(new FloorItemUpdateComposer(item));
            return;
        }

        RoomTile oldTile = room.getLayout().getTile(item.getX(), item.getY());
        double oldZ = item.getZ();

        error = room.moveFurniTo(item, tile, rotation, z, this.client.getUser(), false, true);
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
