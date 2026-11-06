package polaris.hotel.items.interactions.games.tag.bunnyrun;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;

public class InteractionBunnyrunPole extends InteractionTagPole {
    public InteractionBunnyrunPole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBunnyrunPole(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
