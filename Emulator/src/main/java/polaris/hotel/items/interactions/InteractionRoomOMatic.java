package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.messages.outgoing.navigator.OpenRoomCreationWindowComposer;

public class InteractionRoomOMatic extends InteractionDefault {
    public InteractionRoomOMatic(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRoomOMatic(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client != null) {
            client.sendResponse(new OpenRoomCreationWindowComposer());
        }
    }
}
