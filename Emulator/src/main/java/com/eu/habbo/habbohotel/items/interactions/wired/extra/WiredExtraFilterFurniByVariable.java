package com.eu.habbo.habbohotel.items.interactions.wired.extra;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredExtraFilterFurniByVariable extends WiredExtraVariableFilterBase {
    public static final int CODE = 78;

    public WiredExtraFilterFurniByVariable(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredExtraFilterFurniByVariable(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected int getVariableTargetType() {
        return TARGET_FURNI;
    }

    @Override
    protected int getCode() {
        return CODE;
    }
}
