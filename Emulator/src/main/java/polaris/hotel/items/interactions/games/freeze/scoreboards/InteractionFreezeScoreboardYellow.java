package polaris.hotel.items.interactions.games.freeze.scoreboards;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionFreezeScoreboardYellow extends InteractionFreezeScoreboard {
    public static final GameTeamColors TEAM_COLOR = GameTeamColors.YELLOW;

    public InteractionFreezeScoreboardYellow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, TEAM_COLOR);
    }

    public InteractionFreezeScoreboardYellow(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, TEAM_COLOR);
    }
}
