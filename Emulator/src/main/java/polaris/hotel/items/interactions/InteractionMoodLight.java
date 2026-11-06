package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomMoodlightData;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionMoodLight extends UserItem {
    public InteractionMoodLight(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionMoodLight(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
    public void onPlace(Room room) {
        if (room != null) {
            for (RoomMoodlightData data : room.getMoodlightData().values()) {
                if (data.isEnabled()) {
                    this.setExtradata(data.toString());
                    this.needsUpdate(true);
                    room.updateItem(this);
                    Emulator.getThreading().run(this);
                }
            }
        }

        super.onPlace(room);
    }

    @Override
    public boolean isUsable() {
        return true;
    }
}
