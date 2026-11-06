package polaris.hotel.items.interactions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;

public class InteractionRedeemableSubscriptionBox extends InteractionCrackable {
    public InteractionRedeemableSubscriptionBox(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionRedeemableSubscriptionBox(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public boolean userRequiredToBeAdjacent() {
        return false;
    }
}
