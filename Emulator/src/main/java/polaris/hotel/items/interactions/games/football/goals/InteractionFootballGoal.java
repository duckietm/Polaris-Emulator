package polaris.hotel.items.interactions.games.football.goals;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.games.InteractionGameTeamItem;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;

public class InteractionFootballGoal extends InteractionGameTeamItem {
    public InteractionFootballGoal(ResultSet set, Item baseItem, GameTeamColors teamColor) throws SQLException {
        super(set, baseItem, teamColor);
    }

    public InteractionFootballGoal(
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
        return true;
    }

    @Override
    public boolean isWalkable() {
        return this.getBaseItem().allowWalk();
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }
}
