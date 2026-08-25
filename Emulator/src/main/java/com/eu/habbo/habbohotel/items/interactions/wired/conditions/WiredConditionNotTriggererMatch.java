package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotTriggererMatch extends WiredConditionTriggererMatch {
    public static final WiredConditionType type = WiredConditionType.NOT_TRIGGERER_MATCH;

    public WiredConditionNotTriggererMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotTriggererMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        MatchResult result = this.evaluateMatch(ctx);
        return result.valid && !result.matched;
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
