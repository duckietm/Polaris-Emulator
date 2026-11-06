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

public class WiredConditionNotUserHasEffect extends WiredConditionUserHasEffect {
    private static final WiredConditionType type = WiredConditionType.NOT_ACTOR_WEARS_EFFECT;

    public WiredConditionNotUserHasEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserHasEffect(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.userSource);
        if (targets.isEmpty()) return WiredQuantifier.overEmptyNegated(this.getQuantifier() == QUANTIFIER_ALL);

        if (this.getQuantifier() == QUANTIFIER_ALL) {
            return !this.matchesAllTargets(targets);
        }

        return !this.matchesAnyTarget(targets);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
