package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.hotel.wired.core.WiredFreezeUtil;
import polaris.hotel.wired.core.WiredQuantifier;
import polaris.hotel.wired.core.WiredSourceUtil;

/**
 * Passes when the resolved user is NOT frozen. Answers {@link WiredConditionType#NOT_USER_STATE} so the
 * dialog drops the effect id and phrases its quantifier the way a negated condition reads.
 */
public class WiredConditionNotFrozen extends WiredConditionUserHasEffect {
    private static final WiredConditionType type = WiredConditionType.NOT_USER_STATE;

    public WiredConditionNotFrozen(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotFrozen(
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
    protected boolean matchesEffect(RoomUnit roomUnit) {
        return WiredFreezeUtil.isFrozen(roomUnit);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
