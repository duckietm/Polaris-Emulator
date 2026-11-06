package polaris.hotel.items.interactions.wired.effects;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.items.Item;
import polaris.hotel.wired.WiredEffectType;
import polaris.hotel.wired.core.WiredContext;

public class WiredEffectNegativeTriggerStacks extends WiredEffectTriggerStacks {
    public static final WiredEffectType type = WiredEffectType.NEG_CALL_STACKS;

    public WiredEffectNegativeTriggerStacks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeTriggerStacks(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        super.execute(ctx);
    }

    @Override
    protected boolean isNegativeCall() {
        return true;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
