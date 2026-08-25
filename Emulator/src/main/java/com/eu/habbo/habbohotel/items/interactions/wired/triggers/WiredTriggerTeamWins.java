package com.eu.habbo.habbohotel.items.interactions.wired.triggers;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class WiredTriggerTeamWins extends WiredTriggerGameStarts {
    public WiredTriggerTeamWins(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public WiredTriggerTeamWins(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    @Override
    public WiredTriggerType getType() {
        return WiredTriggerType.CUSTOM;
    }
}
