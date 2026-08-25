package com.eu.habbo.habbohotel.items.interactions.wired.selector;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredEffectType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredEffectFurniWithVariable extends WiredEffectVariableSelectorBase {
    public static final WiredEffectType type = WiredEffectType.FURNI_WITH_VAR_SELECTOR;

    public WiredEffectFurniWithVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredEffectFurniWithVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getVariableTargetType() {
        return TARGET_FURNI;
    }

    @Override
    public WiredEffectType getType() {
        return type;
    }
}
