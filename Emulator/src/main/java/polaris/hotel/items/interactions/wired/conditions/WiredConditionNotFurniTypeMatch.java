package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;

public class WiredConditionNotFurniTypeMatch extends WiredConditionFurniTypeMatch {
    public static final WiredConditionType type = WiredConditionType.NOT_STUFF_IS;

    public WiredConditionNotFurniTypeMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotFurniTypeMatch(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        if (ctx == null) {
            return false;
        }

        // The positive box's answer with the same quantifier, turned around: "any" is "not any
        // matches" and "all" is "not all match", as every other negative condition reads it.
        if (this.getQuantifier() == QUANTIFIER_ANY) {
            return !this.evaluateAnyMatches(ctx);
        }

        return !this.evaluateAllMatches(ctx);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }
}
