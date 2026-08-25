package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionGroupPressurePlate extends InteractionPressurePlate {
    public InteractionGroupPressurePlate(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionGroupPressurePlate(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public boolean requiresAllTilesOccupied() {
        return true;
    }
}