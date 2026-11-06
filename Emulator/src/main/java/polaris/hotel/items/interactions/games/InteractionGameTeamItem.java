package polaris.hotel.items.interactions.games;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;
import polaris.hotel.users.UserItem;

public abstract class InteractionGameTeamItem extends UserItem {
    public final GameTeamColors teamColor;

    protected InteractionGameTeamItem(ResultSet set, Item baseItem, GameTeamColors teamColor) throws SQLException {
        super(set, baseItem);

        this.teamColor = teamColor;
    }

    protected InteractionGameTeamItem(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            GameTeamColors teamColor) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.teamColor = teamColor;
    }
}
