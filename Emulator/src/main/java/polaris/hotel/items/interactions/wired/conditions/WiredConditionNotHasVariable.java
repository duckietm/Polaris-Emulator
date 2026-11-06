package polaris.hotel.items.interactions.wired.conditions;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;

public class WiredConditionNotHasVariable extends WiredConditionHasVariable {
    public static final WiredConditionType type = WiredConditionType.NOT_HAS_VAR;

    public WiredConditionNotHasVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredConditionNotHasVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredConditionType getType() {
        return type;
    }

    @Override
    public boolean evaluate(WiredContext ctx) {
        return this.evaluateWithNegation(ctx, true);
    }
}
