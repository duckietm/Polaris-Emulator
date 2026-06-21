package com.eu.habbo.habbohotel.items.interactions.games.battlebanzai;

import com.eu.habbo.habbohotel.games.GameState;
import com.eu.habbo.habbohotel.games.battlebanzai.BattleBanzaiGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;
import org.apache.commons.math3.util.Pair;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

public class InteractionBattleBanzaiTile extends HabboItem {
    public InteractionBattleBanzaiTile(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
        this.setExtradata("0");
    }

    public InteractionBattleBanzaiTile(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
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
    public void onWalk(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void onWalkOn(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {
        super.onWalkOn(roomUnit, room, objects);

        if (this.getExtradata().isEmpty())
            this.setExtradata("0");

        int state = Integer.parseInt(this.getExtradata());

        if (state % 3 == 2)
            return;

        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo == null)
            return;

        if (this.isLocked())
            return;

        if (habbo.getHabboInfo().getCurrentGame() != null && habbo.getHabboInfo().getCurrentGame().equals(BattleBanzaiGame.class)) {
            BattleBanzaiGame game = ((BattleBanzaiGame) room.getGame(BattleBanzaiGame.class));

            if (game == null)
                return;

            if (!game.state.equals(GameState.RUNNING))
                return;

            game.markTile(habbo, this, state);
        }

    }

    public boolean isLocked() {
        if (this.getExtradata().isEmpty())
            return false;

        return Integer.parseInt(this.getExtradata()) % 3 == 2;
    }

    @Override
    public boolean canStackAt(Room room, List<Pair<RoomTile, Set<HabboItem>>> itemsAtLocation) {
        for (Pair<RoomTile, Set<HabboItem>> set : itemsAtLocation) {
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
