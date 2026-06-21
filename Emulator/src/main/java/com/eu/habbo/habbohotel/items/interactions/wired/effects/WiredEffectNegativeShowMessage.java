package com.eu.habbo.habbohotel.items.interactions.wired.effects;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * "Show message" that runs only when the stack's conditions FAIL (the negative branch). Identical to
 * {@link WiredEffectWhisper} except its {@link WiredEffectType#NEG_SHOW_MESSAGE} type makes
 * WiredEngine.isNegativeConditionEffect schedule it on condition failure instead of success. Reuses the
 * SHOW_MESSAGE(7) client dialog (NEG_SHOW_MESSAGE shares code 7).
 */
public class WiredEffectNegativeShowMessage extends WiredEffectWhisper {
    public static final WiredEffectType type = WiredEffectType.NEG_SHOW_MESSAGE;

    public WiredEffectNegativeShowMessage(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectNegativeShowMessage(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
