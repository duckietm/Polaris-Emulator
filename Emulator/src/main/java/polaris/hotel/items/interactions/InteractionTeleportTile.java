package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

public class InteractionTeleportTile extends InteractionTeleport {
    public InteractionTeleportTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTeleportTile(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        if (roomUnit != null && this.canWalkOn(roomUnit, room, objects)) {
            User user = room.getUser(roomUnit);

            if (user != null) {
                if (!canUseTeleport(user.getClient(), room)) return;

                if (!user.getRoomUnit().isTeleporting) {
                    user.getRoomUnit().setGoalLocation(user.getRoomUnit().getCurrentLocation());
                    this.startTeleport(room, user, 1000);
                }
            }
        }
    }
}
