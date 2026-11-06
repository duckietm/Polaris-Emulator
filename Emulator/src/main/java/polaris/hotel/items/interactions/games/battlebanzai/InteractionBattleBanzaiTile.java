package polaris.hotel.items.interactions.games.battlebanzai;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import org.apache.commons.math3.util.Pair;
import polaris.hotel.games.GameState;
import polaris.hotel.games.battlebanzai.BattleBanzaiGame;
import polaris.hotel.items.Item;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.ServerMessage;

public class InteractionBattleBanzaiTile extends UserItem {
    public InteractionBattleBanzaiTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionBattleBanzaiTile(
            int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
        this.setExtradata("0");
    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {}

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata().isEmpty()) this.setExtradata("0");

        int state = Integer.parseInt(this.getExtradata());

        if (state % 3 == 2) return;

        User user = room.getUser(roomUnit);

        if (user == null) return;

        if (this.isLocked()) return;

        if (user.getUserInfo().getCurrentGame() != null
                && user.getUserInfo().getCurrentGame().equals(BattleBanzaiGame.class)) {
            BattleBanzaiGame game = ((BattleBanzaiGame) room.getGame(BattleBanzaiGame.class));

            if (game == null) return;

            if (!game.state.equals(GameState.RUNNING)) return;

            game.markTile(user, this, state);
        }
    }

    public boolean isLocked() {
        if (this.getExtradata().isEmpty()) return false;

        return Integer.parseInt(this.getExtradata()) % 3 == 2;
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<UserItem>>> itemsAtLocation) {
        for (Pair<RoomTile, Set<UserItem>> set : itemsAtLocation) {
            if (set.getValue() != null && !set.getValue().isEmpty()) return false;
        }

        return super.canStackAt(room, itemsAtLocation);
    }

    @Override
    public void onPickUp(Room room) {
        super.onPickUp(room);

        this.setExtradata("0");
        room.updateItem(this);
    }

    @Override
    public void onPlace(Room room) {
        super.onPlace(room);

        BattleBanzaiGame game = (BattleBanzaiGame) room.getGame(BattleBanzaiGame.class);

        if (game != null && game.getState() != GameState.IDLE) {
            this.setExtradata("1");
        }
    }
}
