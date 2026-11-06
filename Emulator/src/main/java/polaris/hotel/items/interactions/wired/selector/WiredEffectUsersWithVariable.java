package polaris.hotel.items.interactions.wired.selector;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredEffectType;

public class WiredEffectUsersWithVariable extends WiredEffectVariableSelectorBase {
    public static final WiredEffectType type = WiredEffectType.USERS_WITH_VAR_SELECTOR;

    public WiredEffectUsersWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectUsersWithVariable(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getVariableTargetType() {
        return TARGET_USER;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
