package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

public class InteractionConfInvisControl extends InteractionRemoteSwitchControl {
    public InteractionConfInvisControl(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionConfInvisControl(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}
