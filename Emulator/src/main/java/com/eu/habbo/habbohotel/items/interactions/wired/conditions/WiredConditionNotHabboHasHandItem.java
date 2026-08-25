package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionNotHabboHasHandItem extends WiredConditionHabboHasHandItem {
    public static final WiredConditionType type = WiredConditionType.NOT_ACTOR_HAS_HANDITEM;

    public WiredConditionNotHabboHasHandItem(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotHabboHasHandItem(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.getUserSource());
        if (targets.isEmpty()) return false;

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
