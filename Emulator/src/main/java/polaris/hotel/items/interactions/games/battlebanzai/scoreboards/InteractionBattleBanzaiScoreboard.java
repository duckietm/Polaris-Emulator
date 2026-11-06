package polaris.hotel.items.interactions.games.battlebanzai.scoreboards;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.InteractionGameScoreboard;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;

public class InteractionBattleBanzaiScoreboard extends InteractionGameScoreboard {
    public InteractionBattleBanzaiScoreboard(ResultSet set, Item baseItem, GameTeamColors teamColor)
            throws SQLException {
        super(set, baseItem, teamColor);
    }

    public InteractionBattleBanzaiScoreboard(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            GameTeamColors teamColor) {
        super(id, userId, item, extradata, limitedStack, limitedSells, teamColor);
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return false;
    }

    @Override
    public boolean isWalkable() {
        return false;
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}
}
