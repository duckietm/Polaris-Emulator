package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.rooms.RoomUserRotation;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.rooms.items.FloorItemOnRollerComposer;

public class InteractionPuzzleBox extends UserItem {
    public InteractionPuzzleBox(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionPuzzleBox(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        RoomTile boxLocation = room.getLayout().getTile(this.getX(), this.getY());
        RoomUserRotation rotation = null;

        if (this.getX() == client.getUser().getRoomUnit().getX()) {
            if (this.getY() == client.getUser().getRoomUnit().getY() + 1) {
                rotation = RoomUserRotation.SOUTH;
            } else if (this.getY() == client.getUser().getRoomUnit().getY() - 1) {
                rotation = RoomUserRotation.NORTH;
            }
        } else if (this.getY() == client.getUser().getRoomUnit().getY()) {
            if (this.getX() == client.getUser().getRoomUnit().getX() + 1) {
                rotation = RoomUserRotation.EAST;
            } else if (this.getX() == client.getUser().getRoomUnit().getX() - 1) {
                rotation = RoomUserRotation.WEST;
            }
        }

        if (rotation == null) {
            RoomTile nearestTile =
                    client.getUser().getRoomUnit().getClosestAdjacentTile(this.getX(), this.getY(), false);

            if (nearestTile != null) client.getUser().getRoomUnit().setGoalLocation(nearestTile);
            return;
        }

        super.onClick(client, room, new Object[] {"TOGGLE_OVERRIDE"});

        RoomTile tile = room.getLayout()
                .getTileInFront(room.getLayout().getTile(this.getX(), this.getY()), rotation.getValue());

        if (tile == null || tile.getState() == RoomTileState.INVALID || room.hasUsersAt(tile.x, tile.y)) {
            return;
        }

        if (!boxLocation.equals(room.getLayout()
                .getTileInFront(client.getUser().getRoomUnit().getCurrentLocation(), rotation.getValue()))) return;

        UserItem item = room.getTopItemAt(tile.x, tile.y);

        if (item != null && !room.getTopItemAt(tile.x, tile.y).getBaseItem().allowStack()) return;

        this.setZ(room.getStackHeight(tile.x, tile.y, false));
        this.needsUpdate(true);
        room.updateItem(this);

        room.scheduledComposers.add(new FloorItemOnRollerComposer(this, null, tile, 0, room).compose());
        room.scheduledTasks.add(() -> {
            client.getUser().getRoomUnit().setGoalLocation(boxLocation);

            room.scheduledTasks.add(() -> client.getUser().getRoomUnit().setGoalLocation(boxLocation));
        });
        this.needsUpdate(true);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}
}
