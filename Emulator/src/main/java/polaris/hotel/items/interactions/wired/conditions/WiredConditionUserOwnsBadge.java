package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;

/**
 * Passes when the resolved user OWNS the given badge (in their inventory), not merely wears it. Reuses
 * the {@link WiredConditionUserWearsBadge} dialog (ACTOR_WEARS_BADGE, badge-code text + source +
 * quantifier) and all of its serialization; the only difference is the owned-vs-worn check, so it needs
 * no new client dialog. This is the OWNED-badge condition that the Phase-A alias deliberately skipped
 * (the wears-badge class only checks worn badges).
 */
public class WiredConditionUserOwnsBadge extends WiredConditionUserWearsBadge {

    public WiredConditionUserOwnsBadge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserOwnsBadge(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean matchesBadge(Room room, RoomUnit roomUnit) {
        User user = room.getUser(roomUnit);
        if (user == null) {
            return false;
        }

        return user.getInventory().getBadgesComponent().hasBadge(this.badge);
    }
}
