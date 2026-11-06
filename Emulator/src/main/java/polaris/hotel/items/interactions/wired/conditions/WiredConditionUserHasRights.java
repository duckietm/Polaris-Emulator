package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.WiredConditionType;

/**
 * Passes when the resolved user has rights in the current room (owner, group rights, or explicitly
 * granted). It keeps {@link WiredConditionUserWearsBadge}'s serialization — the user source and the
 * quantifier are the settings it needs — but answers {@link WiredConditionType#USER_ATTRIBUTE} so the
 * client opens the dialog without the badge-code field.
 */
public class WiredConditionUserHasRights extends WiredConditionUserWearsBadge {

    public WiredConditionUserHasRights(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserHasRights(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        User user = room.getUser(roomUnit);
        return user != null && room.hasRights(user);
    }

    @Override
    public WiredConditionType getType() {
        return WiredConditionType.USER_ATTRIBUTE;
    }
}
