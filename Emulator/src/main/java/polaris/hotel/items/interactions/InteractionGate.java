package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.hotel.wired.WiredEffectType;
import polaris.messages.ServerMessage;

public class InteractionGate extends UserItem {
    public InteractionGate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionGate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    public boolean isWalkable() {
        return this.getExtradata().equals("1");
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        boolean executedByWired = (objects.length >= 2
                && objects[1] instanceof WiredEffectType
                && objects[1] == WiredEffectType.TOGGLE_STATE);

        if (client != null && !room.hasRights(client.getUser()) && !executedByWired) return;

        // If a Habbo is standing on a tile occupied by the gate, the gate shouldn't open/close
        for (RoomTile tile : room.getLayout()
                .getTilesAt(
                        room.getLayout().getTile(this.getX(), this.getY()),
                        this.getBaseItem().getWidth(),
                        this.getBaseItem().getLength(),
                        this.getRotation())) if (room.hasUsersAt(tile.x, tile.y)) return;

        // Gate closed = 0, open = 1
        if (this.getExtradata().length() == 0) this.setExtradata("0");

        this.setExtradata((Integer.parseInt(this.getExtradata()) + 1) % 2 + "");
        room.updateTile(room.getLayout().getTile(this.getX(), this.getY()));
        this.needsUpdate(true);
        room.updateItemState(this);

        super.onClick(client, room, new Object[] {"TOGGLE_OVERRIDE"});
    }

    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOff(roomUnit, room, objects);
    }

    @Override
    public boolean allowWiredResetState() {
        return true;
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
