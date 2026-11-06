package polaris.hotel.items.interactions.mysterybox;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.InteractionDefault;
import polaris.hotel.rooms.Room;
import polaris.hotel.users.User;

/**
 * The key half of the mystery box. Clicking it answers a wait somebody already opened on a box of
 * the same colour: that is the moment both players get their prize and both pieces are used up.
 */
public class InteractionMysteryBoxKey extends InteractionDefault {
    public InteractionMysteryBoxKey(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionMysteryBoxKey(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null || room == null) return;

        User user = client.getUser();

        if (user == null) return;

        MysteryBoxManager.getInstance().clickKey(user, room, this);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
