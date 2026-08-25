package com.eu.habbo.habbohotel.items.interactions.games.football.scoreboards;

import com.eu.habbo.habbohotel.games.GameTeamColors;
import com.eu.habbo.habbohotel.items.Item;

import java.sql.ResultSet;
import java.sql.SQLException;

public class InteractionFootballScoreboardYellow extends InteractionFootballScoreboard {
    public InteractionFootballScoreboardYellow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.YELLOW);
    }

    public InteractionFootballScoreboardYellow(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.YELLOW);
    }
}