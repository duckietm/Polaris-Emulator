package com.eu.habbo.habbohotel.items.interactions.wired.conditions;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredConditionType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredConditionNotFurniTypeMatch extends WiredConditionFurniTypeMatch {
    public static final WiredConditionType type = WiredConditionType.NOT_STUFF_IS;

    public WiredConditionNotFurniTypeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotFurniTypeMatch(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !this.evaluateAllMatches(ctx);
        }

        return !this.evaluateAnyMatches(ctx);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
