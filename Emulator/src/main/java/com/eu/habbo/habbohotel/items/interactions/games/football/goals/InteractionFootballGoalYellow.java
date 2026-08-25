package com.eu.habbo.habbohotel.items.interactions.games.football.goals;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionFootballGoalYellow extends InteractionFootballGoal {
    public InteractionFootballGoalYellow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.YELLOW);
    }

    public InteractionFootballGoalYellow(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.YELLOW);
    }
}