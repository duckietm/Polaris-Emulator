package polaris.hotel.items.interactions.games.football.goals;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionFootballGoalGreen extends InteractionFootballGoal {
    public InteractionFootballGoalGreen(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.GREEN);
    }

    public InteractionFootballGoalGreen(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.GREEN);
    }
}
