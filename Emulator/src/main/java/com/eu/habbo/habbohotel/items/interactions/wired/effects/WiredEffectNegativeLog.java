package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Server-side log that runs only when the stack's conditions FAIL (the negative branch). Identical to
 * {@link WiredEffectLog} except its {@link WiredEffectType#NEG_LOG} type makes
 * WiredEngine.isNegativeConditionEffect schedule it on condition failure instead of success. Reuses the
 * SHOW_MESSAGE(7) client text dialog (NEG_LOG shares code 7).
 */
public class WiredEffectNegativeLog extends WiredEffectLog {
    public static final WiredEffectType type = WiredEffectType.NEG_LOG;

    public WiredEffectNegativeLog(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeLog(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
