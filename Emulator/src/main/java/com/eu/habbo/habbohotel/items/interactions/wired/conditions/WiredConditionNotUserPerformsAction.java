package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.core.WiredSourceUtil;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class WiredConditionNotUserPerformsAction extends WiredConditionUserPerformsAction {
    private static final int QUANTIFIER_ANY_NOT_MATCH = 0;
    private static final int QUANTIFIER_NONE_MATCH = 1;

    public static final WiredConditionType type = WiredConditionType.NOT_USER_PERFORMS_ACTION;

    public WiredConditionNotUserPerformsAction(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotUserPerformsAction(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        List<RoomUnit> targets = WiredSourceUtil.resolveUsers(ctx, this.getUserSource());
        if (targets.isEmpty()) {
            return false;
        }

        if (this.getQuantifier() == QUANTIFIER_NONE_MATCH) {
            return targets.stream().noneMatch(roomUnit -> this.matchesAction(ctx, roomUnit));
        }

        return targets.stream().anyMatch(roomUnit -> !this.matchesAction(ctx, roomUnit));
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
