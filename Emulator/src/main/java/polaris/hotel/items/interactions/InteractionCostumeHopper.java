package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.messages.outgoing.generic.alerts.CustomNotificationComposer;

public class InteractionCostumeHopper extends InteractionHopper {
    public InteractionCostumeHopper(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCostumeHopper(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client.getUser().getRoomUnit().getEffectId() > 0) {
            super.onClick(client, room, objects);
        } else {
            client.sendResponse(new CustomNotificationComposer(CustomNotificationComposer.HOPPER_NO_COSTUME));
        }
    }
}
