package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.messages.outgoing.hotelway.nux.NuxAlertComposer;

public class InteractionInformationTerminal extends InteractionCustomValues {
    public static final Map<String, String> defaultValues = new HashMap<String, String>() {
        {
            this.put("internalLink", "habbopages/chat/commands");
        }
    };

    public InteractionInformationTerminal(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, defaultValues);
    }

    public InteractionInformationTerminal(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, defaultValues);
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalk(roomUnit, room, objects);

        User user = room.getUser(roomUnit);
        if (user != null && this.values.containsKey("internalLink")) {
            user.getClient().sendResponse(new NuxAlertComposer(this.values.get("internalLink")));
        }
    }
}
