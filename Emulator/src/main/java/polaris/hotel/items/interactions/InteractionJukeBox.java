package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionJukeBox extends UserItem {
    public InteractionJukeBox(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionJukeBox(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
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

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null && objects.length == 1) {
            if ((Integer) objects[0] == 0) {
                if (room.getTraxManager().isPlaying()) {
                    room.getTraxManager().stop();
                } else {
                    room.getTraxManager().play(0, client.getUser());
                }
            }
        }
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);
        this.setExtradata("0");
        room.getTraxManager().removeTraxOnRoom(this);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);
        room.getTraxManager().addTraxOnRoom(this);
        if (room.getTraxManager().isPlaying()) {
            this.setExtradata("1");
        }
    }
}
