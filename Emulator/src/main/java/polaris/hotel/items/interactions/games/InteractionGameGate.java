package polaris.hotel.items.interactions.games;

import java.sql.ResultSet;
import java.sql.SQLException;
import polaris.hotel.games.Game;
import polaris.hotel.games.GameTeamColors;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.messages.ServerMessage;

public abstract class InteractionGameGate extends InteractionGameTeamItem {
    public InteractionGameGate(ResultSet set, Item baseItem, GameTeamColors teamColor) throws SQLException {
        super(set, baseItem, teamColor);
        this.setExtradata("0");
    }

    public InteractionGameGate(
            int id,
            int userId,
            Item item,
            String extradata,
            int limitedStack,
            int limitedSells,
            GameTeamColors teamColor) {
        super(id, userId, item, extradata, limitedStack, limitedSells, teamColor);
        this.setExtradata("0");
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    public void updateState(Game game, int maxPlayers) {
        int memberCount = 0;

        if (game.getTeam(this.teamColor) != null) {
            memberCount = game.getTeam(this.teamColor).getMembers().size();
        }

        if (memberCount > maxPlayers) {
            memberCount = maxPlayers;
        }
        this.setExtradata(memberCount + "");
        game.getRoom().updateItem(this);
    }
}
