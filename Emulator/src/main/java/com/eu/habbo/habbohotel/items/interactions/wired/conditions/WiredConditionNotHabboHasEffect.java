package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionNotHabboHasEffect extends WiredConditionHabboHasEffect {
    private static final WiredConditionType type = WiredConditionType.NOT_ACTOR_WEARS_EFFECT;

    public WiredConditionNotHabboHasEffect(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotHabboHasEffect(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
    public WiredConditionType getType() {
        return type;
    }
}
