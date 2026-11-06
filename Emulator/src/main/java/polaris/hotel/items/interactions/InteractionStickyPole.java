package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;

public class InteractionStickyPole extends InteractionDefault {
    public InteractionStickyPole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionStickyPole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        super.onClick(client, room, objects);

        if (client != null && client.getUser() != null) {
            client.getUser()
                    .whisper(Emulator.getTexts()
                            .getValue(
                                    "stickypole.click.info",
                                    "Place a sticky note from your inventory on a wall to leave a message!"));
        }
    }
}
