package polaris.hotel.items.interactions.games.football.goals;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionFootballGoalRed extends InteractionFootballGoal {
    public InteractionFootballGoalRed(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.RED);
    }

    public InteractionFootballGoalRed(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.RED);
    }
}
