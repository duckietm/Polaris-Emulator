package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredFreezeUtil;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Passes when the resolved user is NOT frozen. Answers {@link WiredConditionType#NOT_USER_STATE} so the
 * dialog drops the effect id and phrases its quantifier the way a negated condition reads.
 */
public class WiredConditionNotFrozen extends WiredConditionHabboHasEffect {
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
        if (targets.isEmpty()) return false;

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
