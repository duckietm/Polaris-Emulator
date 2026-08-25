package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;
import com.eu.habbo.habbohotel.wired.core.WiredContext;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectNegativeTriggerStacks extends WiredEffectTriggerStacks {
    public static final WiredEffectType type = WiredEffectType.NEG_CALL_STACKS;

    public WiredEffectNegativeTriggerStacks(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeTriggerStacks(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public void execute(WiredContext ctx) {
        super.execute(ctx);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
