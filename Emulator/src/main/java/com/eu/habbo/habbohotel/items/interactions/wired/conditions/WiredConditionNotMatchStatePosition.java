package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.wired.core.WiredContext;
import com.eu.habbo.habbohotel.wired.WiredConditionType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotMatchStatePosition extends WiredConditionMatchStatePosition {
    public static final WiredConditionType type = WiredConditionType.NOT_MATCH_SSHOT;

    public WiredConditionNotMatchStatePosition(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotMatchStatePosition(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null || ctx.room() == null) {
            return false;
        }

        this.refresh();

        if (this.getMatchFurniSettings().isEmpty()) {
            return false;
        }

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !this.evaluateAnyTargetMatches(ctx);
        }

        return !this.evaluateAllTargetsMatch(ctx);
    }

    @Deprecated
    @Override
    public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
        return false;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
