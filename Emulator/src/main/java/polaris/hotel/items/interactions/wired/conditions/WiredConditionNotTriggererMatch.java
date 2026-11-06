package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;

public class WiredConditionNotTriggererMatch extends WiredConditionTriggererMatch {
    public static final WiredConditionType type = WiredConditionType.NOT_TRIGGERER_MATCH;

    public WiredConditionNotTriggererMatch(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotTriggererMatch(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
