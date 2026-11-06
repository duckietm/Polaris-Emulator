package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;

public class InteractionClubHopper extends InteractionHopper {
    public InteractionClubHopper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionClubHopper(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client.getUser().getUserStats().hasActiveClub()) {
            super.onClick(client, room, objects);
        } else {
            client.sendResponse(new CustomNotificationComposer(CustomNotificationComposer.HOPPER_NO_HC));
        }
    }

    @Override
    protected boolean canUseTeleport(GameClient client, RoomTile front, Room room) {
        return super.canUseTeleport(client, front, room)
                && client.getUser().getUserStats().hasActiveClub();
    }
}
