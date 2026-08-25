package com.eu.habbo.habbohotel.items.interactions.games.football.goals;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionFootballGoalGreen extends InteractionFootballGoal {
    public InteractionFootballGoalGreen(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.GREEN);
    }

    public InteractionFootballGoalGreen(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.GREEN);
    }
}