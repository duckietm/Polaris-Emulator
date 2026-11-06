package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.wired.WiredConditionType;

/**
 * Passes when the resolved user's gender is male. It keeps {@link WiredConditionUserWearsBadge}'s
 * serialization — the user source and the quantifier are exactly the settings this condition needs —
 * but answers {@link WiredConditionType#USER_ATTRIBUTE} so the client opens the dialog without the
 * badge-code field. Gender is not something you type in.
 */
public class WiredConditionUserIsMale extends WiredConditionUserWearsBadge {

    public WiredConditionUserIsMale(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserIsMale(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        User user = room.getUser(roomUnit);
        return user != null && user.getUserInfo().getGender() == UserGender.M;
    }

    @Override
    public WiredConditionType getType() {
        return WiredConditionType.USER_ATTRIBUTE;
    }
}
