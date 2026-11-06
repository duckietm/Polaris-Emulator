package polaris.hotel.items.interactions.games.freeze;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.games.freeze.FreezeGame;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionFreezeTile extends UserItem {
    public InteractionFreezeTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionFreezeTile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        return true;
    }

    @Override
    public boolean isWalkable() {
        return true;
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if (client == null) return;

        if (client.getUser().getRoomUnit().getCurrentLocation().x == this.getX()
                && client.getUser().getRoomUnit().getCurrentLocation().y == this.getY()) {
            FreezeGame game = (FreezeGame) room.getGame(FreezeGame.class);

            if (game != null) game.throwBall(client.getUser(), this);
        }
    }

    @Override
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }

    @Override
    public void onPickUp(Room room) {
        this.setExtradata("0");
    }

    @Override
    public boolean allowWiredResetState() {
        return false;
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<UserItem>>> itemsAtLocation) {
        for (Pair<RoomTile, Set<UserItem>> set : itemsAtLocation) {
            if (set.getValue() != null && !set.getValue().isEmpty()) return false;
        }

        return super.canStackAt(room, itemsAtLocation);
    }
}
