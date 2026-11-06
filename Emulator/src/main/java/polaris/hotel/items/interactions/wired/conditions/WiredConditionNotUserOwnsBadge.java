package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * Negation of {@link WiredConditionUserOwnsBadge}: passes when the user does NOT own the given badge.
 * Mirrors the NOT pattern of {@link WiredConditionNotUserWearsBadge} (inverts the quantified match,
 * empty targets -> true) and reuses the same badge dialog via NOT_ACTOR_WEARS_BADGE.
 */
public class WiredConditionNotUserOwnsBadge extends WiredConditionUserOwnsBadge {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_WEARS_BADGE;

    public WiredConditionNotUserOwnsBadge(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserOwnsBadge(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return true;

        if (this.getQuantifier() == QUANTIFIER_ALL) {
            return !this.matchesAllTargets(room, targets);
        }

        return !this.matchesAnyTarget(room, targets);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
