package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * Negation of {@link WiredConditionUserOwnsFurni}: passes when the triggering user does NOT own furni of
 * the selected type(s). Inherits FURNI_PROPERTY from its parent, so it gets the same picker without the
 * counter-only gate that the altitude dialog imposed.
 */
public class WiredConditionUserNotOwnsFurni extends WiredConditionUserOwnsFurni {

    public WiredConditionUserNotOwnsFurni(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionUserNotOwnsFurni(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        if (room == null) {
            return false;
        }

        this.refresh(room);

        HashSet<Integer> typeIds = this.resolveTypeIds(ctx);
        if (typeIds.isEmpty()) {
            return true;
        }

        List<RoomUnit> users = WiredSourceUtil.resolveUsers(ctx, WiredSourceUtil.SOURCE_TRIGGER);
        if (users.isEmpty()) {
            return true;
        }

        for (RoomUnit unit : users) {
            User user = room.getUser(unit);
            if (user != null && !this.userOwns(user, typeIds)) {
                return true;
            }
        }

        return false;
    }
}
