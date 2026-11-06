package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * Negation of {@link WiredConditionHasTag}: passes when the resolved user does NOT have the configured
 * profile tag. Mirrors the NOT-condition pattern (invert the quantified match, empty targets -> true) and
 * reuses the same tag text dialog via NOT_ACTOR_WEARS_BADGE.
 */
public class WiredConditionNotHasTag extends WiredConditionHasTag {
    public static final WiredConditionType type = WiredConditionType.NOT_USER_TAG;

    public WiredConditionNotHasTag(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotHasTag(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        Room room = ctx.room();
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmptyNegated(this.getQuantifier() == QUANTIFIER_ALL);

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
