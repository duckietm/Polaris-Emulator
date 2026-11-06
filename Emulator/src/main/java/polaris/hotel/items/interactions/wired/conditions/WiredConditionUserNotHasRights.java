package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredConditionType;

/**
 * Passes when the resolved user does NOT have rights in the current room (the negation of
 * {@link WiredConditionUserHasRights}). Answers {@link WiredConditionType#NOT_USER_ATTRIBUTE} rather
 * than the positive code so the dialog phrases its quantifier the way a negated condition reads —
 * "none of the users" instead of "any of them".
 */
public class WiredConditionUserNotHasRights extends WiredConditionUserHasRights {

    public WiredConditionUserNotHasRights(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserNotHasRights(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        User user = room.getUser(roomUnit);
        return user != null && !room.hasRights(user);
    }

    @Override
    public WiredConditionType getType() {
        return WiredConditionType.NOT_USER_ATTRIBUTE;
    }
}
