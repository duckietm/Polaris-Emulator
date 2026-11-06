package polaris.hotel.items.interactions.games.freeze.gates;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionFreezeGateBlue extends InteractionFreezeGate {
    public static final GameTeamColors TEAM_COLOR = GameTeamColors.BLUE;

    public InteractionFreezeGateBlue(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, TEAM_COLOR);
    }

    public InteractionFreezeGateBlue(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, TEAM_COLOR);
    }
}
