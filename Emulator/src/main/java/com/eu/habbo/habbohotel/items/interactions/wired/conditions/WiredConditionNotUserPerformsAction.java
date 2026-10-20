package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredQuantifier;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionNotUserPerformsAction extends WiredConditionUserPerformsAction {
    public static final WiredConditionType type = WiredConditionType.NOT_USER_PERFORMS_ACTION;

    public WiredConditionNotUserPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserPerformsAction(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.getUserSource());
        if (targets.isEmpty()) {
            return WiredQuantifier.overEmptyNegated(this.getQuantifier() != QUANTIFIER_ANY);
        }

        // The positive box's answer with the same quantifier, turned around: "any" is "not any
        // acts" and "all" is "not all act", as every other negative condition reads it.
        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !targets.stream().anyMatch(roomUnit -> this.matchesAction(ctx, roomUnit));
        }

        return !targets.stream().allMatch(roomUnit -> this.matchesAction(ctx, roomUnit));
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
