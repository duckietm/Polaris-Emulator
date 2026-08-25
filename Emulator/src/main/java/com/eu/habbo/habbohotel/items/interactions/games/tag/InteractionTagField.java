package com.eu.habbo.habbohotel.items.interactions.games.tag;

import com.eu.habbo.habbohotel.games.Game;
import com.eu.habbo.habbohotel.games.tag.TagGame;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ServerMessage;

import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class InteractionTagField extends HabboItem {
    public Class<? extends Game> gameClazz;

    public InteractionTagField(ResultSet set, Item baseItem, Class<? extends Game> gameClazz) throws SQLException {
        super(set, baseItem);

        this.gameClazz = gameClazz;
    }

    public InteractionTagField(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells, Class<? extends Game> gameClazz) {
        super(id, userId, item, extradata, limitedStack, limitedSells);

        this.gameClazz = gameClazz;
    }

    @Override
    public boolean canWalkOn(RoomUnit roomUnit, Room room, Object[] objects) {
        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            return habbo.getHabboInfo().getCurrentGame() == null || habbo.getHabboInfo().getCurrentGame() == this.gameClazz;
        }

        return false;
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
        Habbo habbo = room.getHabbo(roomUnit);

        if (habbo != null) {
            if (habbo.getHabboInfo().getCurrentGame() == null) {
                TagGame game = (TagGame) room.getGame(this.gameClazz);

                if (game == null) {
                    game = (TagGame) this.gameClazz.getDeclaredConstructor(Room.class).newInstance(room);
                    room.addGame(game);
                }

                game.addHabbo(habbo, null);
                habbo.getHabboInfo().setCurrentGame(this.gameClazz);
            }
        }
    }

    @Override
    public void onWalkOff(RoomUnit roomUnit, Room room, Object[] objects) throws Exception {

    }

    @Override
    public void serializeExtradata(ServerMessage serverMessage) {
        serverMessage.appendInt((this.isLimited() ? 256 : 0));
        serverMessage.appendString(this.getExtradata());

        super.serializeExtradata(serverMessage);
    }
}