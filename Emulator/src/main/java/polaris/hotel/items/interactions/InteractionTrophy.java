package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

public class InteractionTrophy extends InteractionDefault {
    public InteractionTrophy(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTrophy(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }
}
