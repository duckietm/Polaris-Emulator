package polaris.hotel.items.interactions.games.battlebanzai.gates;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;

public class InteractionBattleBanzaiGateYellow extends InteractionBattleBanzaiGate {
    public static final GameTeamColors TEAM_COLOR = GameTeamColors.YELLOW;

    public InteractionBattleBanzaiGateYellow(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem, TEAM_COLOR);
    }

    public InteractionBattleBanzaiGateYellow(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells, TEAM_COLOR);
    }
}
