package polaris.hotel.items.interactions.games.tag.icetag;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.tag.InteractionTagPole;

public class InteractionIceTagPole extends InteractionTagPole {
    public InteractionIceTagPole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionIceTagPole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
