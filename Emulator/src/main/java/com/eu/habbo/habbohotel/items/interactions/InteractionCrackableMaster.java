package com.eu.habbo.habbohotel.items.interactions;

import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionCrackableMaster extends InteractionCrackable {
    public InteractionCrackableMaster(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionCrackableMaster(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    protected boolean placeInRoom() {
        return false;
    }

    @Override
    public boolean resetable() {
        return true;
    }

    @Override
    public boolean allowAnyone() {
        return true;
    }
}
