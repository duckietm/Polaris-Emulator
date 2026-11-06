package polaris.hotel.items.interactions.games.football.scoreboards;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionFootballScoreboardYellow extends InteractionFootballScoreboard {
    public InteractionFootballScoreboardYellow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, GameTeamColors.YELLOW);
    }

    public InteractionFootballScoreboardYellow(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, GameTeamColors.YELLOW);
    }
}
