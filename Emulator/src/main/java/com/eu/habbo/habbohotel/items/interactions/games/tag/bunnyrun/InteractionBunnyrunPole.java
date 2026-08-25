package com.eu.habbo.habbohotel.items.interactions.games.tag.bunnyrun;

import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.games.tag.InteractionTagPole;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionBunnyrunPole extends InteractionTagPole {
    public InteractionBunnyrunPole(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionBunnyrunPole(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }
}