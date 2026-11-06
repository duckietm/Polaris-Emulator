package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;

public class WiredConditionNotUserHasHandItem extends WiredConditionUserHasHandItem {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_HAS_HANDITEM;

    public WiredConditionNotUserHasHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserHasHandItem(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.getUserSource());
        if (targets.isEmpty()) return WiredQuantifier.overEmptyNegated(this.getQuantifier() != QUANTIFIER_ANY);

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !this.matchesAnyTarget(targets);
        }

        return !this.matchesAllTargets(targets);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
