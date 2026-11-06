package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

public class InteractionClubTeleportTile extends InteractionTeleportTile {
    public InteractionClubTeleportTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionClubTeleportTile(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        User user = room.getUser(roomUnit);

        if (user != null) {
            return user.getUserStats().hasActiveClub();
        }

        return false;
    }

    @Override
    public boolean canUseTeleport(GameClient client, Room room) {
        return super.canUseTeleport(client, room)
                && client.getUser().getUserStats().hasActiveClub();
    }
}
