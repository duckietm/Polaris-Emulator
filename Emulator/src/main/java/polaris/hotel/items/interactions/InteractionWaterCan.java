package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

public class InteractionWaterCan extends InteractionDefault {
    private static final int WATER_CAN_EFFECT = 192;
    private static final int EFFECT_DURATION_SECONDS = 30;

    public InteractionWaterCan(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionWaterCan(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || client.getUser() == null || room == null) {
            return;
        }

        room.giveEffect(client.getUser(), WATER_CAN_EFFECT, EFFECT_DURATION_SECONDS);
    }
}
